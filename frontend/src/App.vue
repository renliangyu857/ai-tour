<template>
  <RouterView />
</template>

<style>
*, *::before, *::after {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

:root {
  --cream:        #F5EFE7;
  --cream-200:    #EDE4D8;
  --cream-300:    #D9CCBC;
  --forest:       #1C3829;
  --forest-600:   #2A5040;
  --forest-400:   #3D7060;
  --earth:        #8B5A3C;
  --earth-light:  #C07A52;
  --gold:         #B8923A;
  --text:         #1A1614;
  --text-2:       #5C4E42;
  --text-3:       #9A8C82;
  --white:        #FFFFFF;
  --shadow-sm:    0 1px 4px rgba(28,56,41,0.08);
  --shadow-md:    0 4px 20px rgba(28,56,41,0.12);
  --shadow-lg:    0 8px 40px rgba(28,56,41,0.16);
  --radius:       10px;
  --radius-lg:    16px;
}

html, body {
  height: 100%;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}

body {
  font-family: 'DM Sans', 'PingFang SC', 'Microsoft YaHei', sans-serif;
  color: var(--text);
  background: var(--cream);
}

/* Scrollbar */
::-webkit-scrollbar { width: 5px; height: 5px; }
::-webkit-scrollbar-track { background: transparent; }
::-webkit-scrollbar-thumb { background: var(--cream-300); border-radius: 3px; }
::-webkit-scrollbar-thumb:hover { background: var(--text-3); }

/* Element Plus overrides */
.el-button--primary {
  --el-button-bg-color: var(--forest) !important;
  --el-button-border-color: var(--forest) !important;
  --el-button-hover-bg-color: var(--forest-600) !important;
  --el-button-hover-border-color: var(--forest-600) !important;
}
</style>
