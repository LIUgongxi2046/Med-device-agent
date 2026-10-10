function installComplexDemo({materials,tasks,projects,createTask,knowledge}) {
  const specs=[
    ['型号适用清单','v2.1','approved','型号 A 与型号 B 共用主机；型号 C 使用独立采集附件。','设计输入 · 型号表'],
    ['性能指标比对记录','v1.3','pending','设计输入记录采样频率为 500 Hz，验证报告记录为 250 Hz；适用模式尚待研发确认。','比对记录 · 第 3 项'],
    ['验证报告扫描件','v1.0','pending','扫描件第 12 页单位识别存在疑问，原件尚待人工校核。','扫描件 · 第 12 页'],
    ['附件兼容性矩阵','v3.0','approved','附件 AX-10 适用于型号 A；AX-20 适用于型号 B。型号 C 的组合验证尚未完成。','兼容性表 · 第 4 行'],
    ['标签与说明书校对记录','v2.0','pending','说明书使用环境上限与技术要求稿件不一致；两个版本需依据原始验证记录确认。','校对记录 · 使用环境'],
    ['风险控制验证索引','v1.2','approved','风险项目 R-07 关联附件误用控制。证据见附件兼容性矩阵，保留适用型号限制。','风险索引 · R-07'],
    ['软件发布说明','v2.4','approved','软件版本 2.4；增加异常数据提示。算法性能结论保持待审，禁止延用未核验结论。','发布记录 · 变更范围'],
    ['外部实验记录','v0.9','pending','实验条件缺少温度记录，设备编号已登记，待补充原始签字页。','实验记录 · 条件表'],
    ['历史技术要求','v1.0','disabled','历史型号名称用于版本比较。本资料已停用，不作为新文稿依据。','历史版本 · 型号章节'],
    ['供应商材料声明','v1.0','pending','供应商已提供材料类别，附件批次与适用产品关系尚待确认。','材料声明 · 附件批次'],
    ['结构组成差异表','v2.2','approved','型号 A 配置标准采集模块；型号 B 增加隔离附件。差异需同步目录及结构章节。','结构表 · A/B 型号'],
    ['审查问题回复草稿','v0.4','pending','问题回复包含候选解释；需要引用经核准的原始证据后提交审查。','回复稿 · 问题一'],
    ['产品名称变更说明','v1.1','approved','项目产品名称保持建档值。附件商品名称不能替代注册产品名称。','变更说明 · 第一节'],
    ['寿命验证补充计划','v0.3','pending','待完成补充验证与结果评审；当前计划不能替代验证结果。','补充计划 · 验收条件'],
    ['运输条件验证记录','v1.0','approved','运输条件记录与包装方案关联。引用时须同时核对包装版本。','验证记录 · 条件表'],
    ['包装方案','v2.0','approved','包装组合依据附件种类区分；运输验证关联方案 v1.0，当前版本需要影响评估。','包装方案 · 版本记录'],
    ['法规适用性核查记录','v0.2','pending','候选文件的实施状态、适用产品与引用条款待知识审核人员确认。','适用性清单 · 候选项'],
    ['检验方法对应表','v1.2','approved','每项性能指标关联检验方法和验证记录。采样频率冲突未关闭，相关结论暂不签发。','方法表 · 第 3 项']
  ];
  const added=specs.map(([name,version,status,text,location],index)=>{
    const item={id:`sample-${index+1}`,name:`${name}（演示）`,version,status,text:`演示资料，不代表真实产品或验证结论。\n${text}`,location,category:'project',folderId:'project',scope:'project',projectId:projects[0].id,author:['周予安','沈知微','顾明川'][index%3],source:'复杂案例演示资料',updated:`2026-09-${String(27-index%5).padStart(2,'0')} 10:00`,parsed:index!==2,uploaded:false,tags:['复杂案例',status==='pending'?'待核查':'版本依据'],favorite:index===0,deleted:false,activity:[],versions:[],demo:true};
    item.versions.push({version,text:item.text,status,location,updated:item.updated});
    if(index===15)item.versions.unshift({version:'v1.0',text:'演示资料：历史包装方案仅包含标准附件。',status:'approved',location:'包装方案 v1.0 · 第二节',updated:'2026-09-18'});
    if(index===2)item.processing={upload:'演示 · 已接收',parse:'演示 · OCR 待核查',index:'演示 · 未建立',version:'演示处理 v1',issue:'第 12 页单位识别待核查；没有执行真实 OCR'};
    return item;
  });
  materials.push(...added);
  const generated=[
    ['多型号技术要求与证据冲突审查','待人工审查','林知远'],
    ['软件变更与跨章影响评估','已退回','周予安'],
    ['附件组合验证与资料补充','待补资料','沈知微']
  ].map(([name,status,owner],index)=>{
    const task=createTask(projects[0],'review',status,owner);task.name=`${name}（演示）`;task.demo=true;task.due=['2026-09-30','2026-10-02','2026-10-06'][index];task.run='演示案例 · 等待人工处理';
    const indices=index===0?[0,10,1,17]:index===1?[6,10,4,7]:[0,3,13,17];
    task.modules.forEach((m,i)=>{const source=added[indices[i]];Object.assign(m,{sourceId:source.id,sourceVersion:source.version,sourceText:source.text,location:source.location,text:`${m.name}\n${source.text}\n当前为候选文稿。范围、字段和结论需要负责人依据原始资料核验。`})});
    task.history[0].texts=task.modules.map(m=>m.text);
    task.comments=[{module:2,quote:task.modules[2].text.slice(0,12),text:'采样频率存在两组取值，请补充适用条件与原文依据。',person:'林知远',time:'2026-09-27 09:25',resolved:false}];
    return task;
  });
  const definitions=[
    ['核对各型号适用范围','产品概述','周予安','待核验',[0,3],'逐项核对型号、附件及适用条件',[]],
    ['确认采样频率冲突','性能指标','周予安','证据冲突',[1,7],'依据原始验证记录确认取值及条件',[0]],
    ['补充扫描件单位证据','检验方法','沈知微','待补证',[2],'核对第 12 页原件并补充清晰文件',[]],
    ['核对包装变更影响','结构与组成','顾明川','版本变化',[14,15],'确认新包装是否需要补充验证',[0]],
    ['复核软件变更描述','产品概述','林知远','已核验',[6],'描述与已核准发布说明逐项一致',[]],
    ['整理指标与方法对应关系','检验方法','林知远','等待前置',[1,17],'上游指标确认后逐项匹配方法',[1,2]]
  ];
  generated.forEach(task=>{
    const members=added.filter(x=>x.status!=='disabled').map(x=>knowledge.snap(x));
    task.research={scope:{mode:'approved',network:false,urls:'',domains:'',terms:'',collection:'',members,revision:2},questions:definitions.map(([question,chapter,owner,status,ids,condition,dependencies])=>({question,chapter,owner,status,condition,dependencies,sources:ids.map(i=>`${added[i].id}:${added[i].version}`),notes:[],blocking:true})),requests:[],revision:2,panel:'questions',timeline:[{time:'2026-09-27 09:12',kind:'计划',text:'演示：按型号范围与验证问题组织研究计划。'},{time:'2026-09-27 09:18',kind:'证据',text:'演示：发现采样频率冲突，相关章节等待人工补证。'},{time:'2026-09-27 09:24',kind:'版本',text:'演示：包装方案存在新版本，历史引用保持原版本。'}]};
  });
  knowledge.seedDemo(added,generated);
  return generated;
}
