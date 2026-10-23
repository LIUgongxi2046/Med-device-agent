import { readFileSync } from 'node:fs'

const report = JSON.parse(readFileSync(new URL('../.implementation-work/document-read-performance.json', import.meta.url)))
const leaves = new Map(), application = new Map(), threads = new Map()
const count = (map, key) => map.set(key, (map.get(key) ?? 0) + 1)
for (const event of report.recording.events) {
  const values = event.values, frames = values.stackTrace?.frames ?? []
  count(threads, values.sampledThread?.javaName ?? 'UNKNOWN')
  const name = frame => frame.method.type.name + '.' + frame.method.name
  if (frames.length) count(leaves, name(frames[0]))
  for (const method of new Set(frames.filter(frame => frame.method.type.name.startsWith('com/ruoyi/')).map(name))) count(application, method)
}
const top = map => [...map.entries()].sort((a, b) => b[1] - a[1]).slice(0, 16)
console.log(JSON.stringify({ samples: report.recording.events.length, threads: top(threads), leaves: top(leaves), application: top(application) }, null, 2))
