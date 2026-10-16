import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import SelectionCheck from './selection-check.vue'

createApp(SelectionCheck).use(ElementPlus).mount('#app')
