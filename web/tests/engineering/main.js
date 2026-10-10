import { createApp, defineComponent, ref, h } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import SvgIcon from '../../src/components/SvgIcon/index.vue'
import IconSelect from '../../src/components/IconSelect/index.vue'
import icons from '../../src/components/IconSelect/requireIcons.js'
import { createCacheChartOptions } from '../../src/utils/cache-chart-options.js'
import * as echarts from 'echarts'
import 'echarts/theme/v5'

const selector = ref(null)
const selected = ref('')
const App = defineComponent({
  setup() {
    return () => h('main', [
      h('section', { 'data-catalogue': '' }, icons.map(name =>
        h(SvgIcon, { iconClass: name, className: 'engineering-icon', color: '#123456', 'data-icon': name }))),
      h(IconSelect, { ref: selector, activeIcon: selected.value, onSelected: name => { selected.value = name } }),
      h('output', { 'data-selected': '' }, selected.value),
      h('button', { 'data-reset': '', onClick: () => selector.value.reset() }, '重置筛选')
    ])
  }
})
createApp(App).use(ElementPlus).component('svg-icon', SvgIcon).mount('#app')

const metrics = __WEB_REDIS_METRICS__
const options = createCacheChartOptions(metrics.cache)
const commands = echarts.init(document.getElementById('commands'), 'v5')
const memory = echarts.init(document.getElementById('memory'), 'v5')
commands.setOption(options.commands)
memory.setOption(options.memory)

window.engineeringCheck = {
  icons,
  metrics,
  chartVersion: echarts.version,
  chartOptions: () => ({ commands: commands.getOption(), memory: memory.getOption() }),
  resize: () => {
    commands.resize({ width: 480, height: 320 })
    memory.resize({ width: 480, height: 320 })
    return [commands.getWidth(), commands.getHeight(), memory.getWidth(), memory.getHeight()]
  },
  dispose: () => {
    commands.dispose()
    memory.dispose()
    return commands.isDisposed() && memory.isDisposed()
  }
}
