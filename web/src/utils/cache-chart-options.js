export function createCacheChartOptions(cache) {
  // 页面和真实数据兼容检查共同使用完整图表参数。
  return {
    commands: {
      tooltip: {
        trigger: 'item',
        formatter: '{a} <br/>{b} : {c} ({d}%)'
      },
      series: [{
        name: '命令',
        type: 'pie',
        roseType: 'radius',
        radius: [15, 95],
        center: ['50%', '38%'],
        data: cache.commandStats,
        animationEasing: 'cubicInOut',
        animationDuration: 1000
      }]
    },
    memory: {
      tooltip: {
        formatter: '{b} <br/>{a} : ' + cache.info.used_memory_human
      },
      series: [{
        name: '峰值',
        type: 'gauge',
        min: 0,
        max: 1000,
        detail: { formatter: cache.info.used_memory_human },
        data: [{
          value: parseFloat(cache.info.used_memory_human),
          name: '内存消耗'
        }]
      }]
    }
  }
}
