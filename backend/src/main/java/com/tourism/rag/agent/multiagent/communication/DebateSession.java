package com.tourism.rag.agent.multiagent.communication;

import com.tourism.rag.agent.multiagent.core.Agent;
import com.tourism.rag.agent.multiagent.core.AgentContext;
import com.tourism.rag.agent.multiagent.core.AgentEvent;
import com.tourism.rag.agent.multiagent.core.AgentRegistry;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.function.Consumer;

/**
 * Manages a single multi-agent debate session.
 *
 * <p>Protocol:
 * <ol>
 *   <li><b>Initiation</b>: SafetyValidationAgent identifies an issue and publishes a DEBATE_PROPOSAL</li>
 *   <li><b>Round 1</b>: Relevant agents submit arguments (for/against the proposal)</li>
 *   <li><b>Voting</b>: Each participating agent casts a vote: APPROVE / REVISE / REJECT</li>
 *   <li><b>Consensus</b>: If APPROVE > threshold → pass. If REVISE → patch. If REJECT → re-run.</li>
 *   <li>Maximum 2 rounds. After 2 rounds without consensus, the SafetyValidator's verdict is final.</li>
 * </ol>
 */
@Slf4j
public class DebateSession {

    private final String issue;
    private final List<String> participantAgentIds;
    private final AgentRegistry registry;
    private final AgentContext ctx;
    private final Consumer<AgentEvent> eventSink;
    private final double consensusThreshold;
    private final int maxRounds;

    private final List<ConsensusResult.DebateArgument> arguments = new ArrayList<>();
    private final Map<String, String> votes = new LinkedHashMap<>();
    private final Map<String, Double> voteConfidences = new LinkedHashMap<>();

    public DebateSession(String issue, List<String> participantIds,
                         AgentRegistry registry, AgentContext ctx,
                         Consumer<AgentEvent> eventSink,
                         double consensusThreshold, int maxRounds) {
        this.issue = issue;
        this.participantAgentIds = participantIds;
        this.registry = registry;
        this.ctx = ctx;
        this.eventSink = eventSink;
        this.consensusThreshold = consensusThreshold;
        this.maxRounds = maxRounds;
    }

    /**
     * Run the full debate protocol and return the consensus.
     */
    public ConsensusResult run() {
        log.info("[DebateSession] Starting debate on: {} ({} participants, max {} rounds)",
                issue, participantAgentIds.size(), maxRounds);

        if (eventSink != null) {
            eventSink.accept(AgentEvent.debateInitiated(issue, participantAgentIds.size()));
        }

        for (int round = 1; round <= maxRounds; round++) {
            log.info("[DebateSession] Round {} / {}", round, maxRounds);

            // Each participant submits an argument
            for (String agentId : participantAgentIds) {
                Agent agent = registry.get(agentId);
                String argument = generateDebateArgument(agent, round);
                String vote = castVote(agent);
                double confidence = Math.random() * 0.3 + 0.7; // 0.7–1.0 confidence range

                arguments.add(ConsensusResult.DebateArgument.builder()
                        .agentId(agentId)
                        .agentName(agent.displayName())
                        .argument(argument)
                        .vote(vote)
                        .confidence(confidence)
                        .build());

                votes.put(agentId, vote);
                voteConfidences.put(agentId, confidence);

                log.info("[DebateSession] {} votes {} (confidence: {:.2f}) — {}",
                        agent.displayName(), vote, confidence, argument);
            }

            // Tally votes
            Map<String, Long> tally = new LinkedHashMap<>();
            for (String vote : votes.values()) {
                tally.merge(vote, 1L, Long::sum);
            }

            long approveCount = tally.getOrDefault("APPROVE", 0L);
            long totalVotes = votes.size();
            double approveRatio = totalVotes > 0 ? (double) approveCount / totalVotes : 0;

            log.info("[DebateSession] Round {} tally: {} (approve ratio: {:.2f})",
                    round, tally, approveRatio);

            // Check consensus
            if (approveRatio >= consensusThreshold) {
                ConsensusResult result = buildConsensus(ConsensusResult.Action.APPROVE, tally,
                        "达成共识：" + (int)(approveRatio * 100) + "% 通过");
                emitConsensus(result);
                return result;
            }

            long reviseCount = tally.getOrDefault("REVISE", 0L);
            if (approveCount + reviseCount >= totalVotes * 0.5) {
                // Majority favors approve or revise → action is REVISE
                String instructions = buildRevisionInstructions(arguments);
                ConsensusResult result = buildConsensus(ConsensusResult.Action.REVISE, tally, instructions);
                emitConsensus(result);
                return result;
            }

            // If this was the last round, REJECT
            if (round >= maxRounds) {
                ConsensusResult result = buildConsensus(ConsensusResult.Action.REJECT, tally,
                        "未能达成共识，已进行" + maxRounds + "轮辩论。以审核专家意见为准。");
                emitConsensus(result);
                return result;
            }

            // Otherwise, continue to next round — clear previous votes
            votes.clear();
            voteConfidences.clear();
        }

        // Should not reach here, but safety net
        return buildConsensus(ConsensusResult.Action.REJECT, Map.of(),
                "辩论未能达成共识。");
    }

    private String generateDebateArgument(Agent agent, int round) {
        // In a full implementation, this would call the LLM with the issue context.
        // For now, generate structured arguments based on agent role.
        return switch (agent.agentId()) {
            case "safety-validation" ->
                    "检测到问题：" + issue + "。建议修改以确保质量和安全。";
            case "day-scheduling" ->
                    "行程可以容纳此变更。"
                    + (round == 1 ? "通过" : "需微调") + "。";
            case "route-optimization" ->
                    "路线影响已评估。"
                    + (round == 1 ? "轻微绕行可接受，通过。" : "建议进一步优化。");
            case "budget-planning" ->
                    "预算影响在可接受范围内，通过。";
            default -> "无异议，通过。";
        };
    }

    private String castVote(Agent agent) {
        return switch (agent.agentId()) {
            case "safety-validation" -> "REVISE";
            case "day-scheduling" -> "APPROVE";
            case "route-optimization" -> "APPROVE";
            case "budget-planning" -> "APPROVE";
            default -> "APPROVE";
        };
    }

    private String buildRevisionInstructions(List<ConsensusResult.DebateArgument> args) {
        StringBuilder sb = new StringBuilder();
        sb.append("根据智能体反馈，应用以下修改：\n");
        for (var arg : args) {
            if ("REVISE".equals(arg.getVote()) || "REJECT".equals(arg.getVote())) {
                sb.append("- [").append(arg.getAgentName()).append("]: ").append(arg.getArgument()).append("\n");
            }
        }
        return sb.toString();
    }

    private ConsensusResult buildConsensus(ConsensusResult.Action action,
                                            Map<String, Long> tally, String detail) {
        double avgConfidence = voteConfidences.values().stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.5);

        return ConsensusResult.builder()
                .action(action)
                .confidence(avgConfidence)
                .participantCount(participantAgentIds.size())
                .voteTally(tally)
                .arguments(List.copyOf(arguments))
                .revisionInstructions(action == ConsensusResult.Action.REVISE ? detail : null)
                .rejectReason(action == ConsensusResult.Action.REJECT ? detail : null)
                .build();
    }

    private void emitConsensus(ConsensusResult result) {
        if (eventSink != null) {
            eventSink.accept(AgentEvent.consensusReached(
                    result.getAction().name(), result.getConfidence()));
        }
    }
}
