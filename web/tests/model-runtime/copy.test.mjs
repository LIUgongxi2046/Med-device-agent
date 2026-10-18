import test from 'node:test'
import assert from 'node:assert/strict'
import {readFileSync} from 'node:fs'
import {createHash} from 'node:crypto'
import {createRequire} from 'node:module'
import {webRoot} from '../formal-capability/helpers.mjs'
const require=createRequire(import.meta.url),{parse}=require('@vue/compiler-sfc'),{baseParse}=require('@vue/compiler-dom')
const descriptor=parse(readFileSync(webRoot+'/src/views/medical/model-runtime.vue','utf8')).descriptor,ast=baseParse(descriptor.template.content)
const hash=v=>createHash('sha256').update(v).digest('hex')
function controls(node,out=[]){if(node.type===1)for(const p of node.props)if(p.type===7)out.push([p.name,p.arg?.content??null,p.exp?.content??null,p.modifiers.map(m=>typeof m==='string'?m:m.content)].join('|'));for(const c of node.children??[])controls(c,out);return out.sort()}
test('copy-only model page preserves exact script and every gate/event/model binding',()=>{assert.equal(hash(descriptor.scriptSetup.content),'c044fb8494cf4f32f07dd070ee920dfbb5942965e81e426f459bc728844648af');assert.equal(hash(JSON.stringify(controls(ast))),'57be838cabe3d0c0544ba81d9f7ab8d98ac8062df38df754ed2eb522d7674725')})
function textOutsideDetails(node,inside=false){inside=inside||node.type===1&&node.tag==='details';return (node.type===2&&!inside?node.content:'')+(node.children??[]).map(n=>textOutsideDetails(n,inside)).join('')}
test('main copy keeps charge and uncertain-cancel risks visible while technical explanations are collapsed',()=>{const text=textOutsideDetails(ast);assert.match(text,/可能产生供应商费用/);assert.match(text,/发送后取消不保证终止或退款/);assert.match(text,/不会释放未知费用预留/);assert.match(text,/不换键、不重发/);assert.match(text,/2 MiB/);assert.doesNotMatch(text,/085|076|MODEL_ENABLEMENT|reasoning_effort/);assert.match(descriptor.template.content,/技术详情/);assert.match(text,/发起验证/);assert.match(text,/申请启用/)})
