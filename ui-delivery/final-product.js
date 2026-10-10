function createFinalProduct(ui){
  const {escape:e,button:baseButton,tag,options,table,tabs,field}=WorkspaceUI;
  const b=(label,action,value='',primary=false,disabled=false)=>baseButton(label,action,value,primary,disabled||(action==='final-scene'&&value!=='document'));
  const scenes=[['document','注册文档','模板、产品资料与法规','正文、引用与检查报告'],['quality','材料质量检查','已有申报文档与检查规则','问题清单与修订意见'],['regulation','法规版本比较','旧版、新版及适用项目','条款变化与项目影响'],['report','综述与报告','研究目标与指定资料','报告与来源附录'],['translation','多语言翻译','原文、目标语言与术语表','双语对照与术语核查'],['market','市场分析','产品、市场范围与公开来源','市场报告与证据'],['question','多模态问答','文档、图片与提问','回答、图片位置与引用']];
  const strategies=['指定资料取值','按法规或模板填写','限定范围编写','严格字段核验'];
  const dimensions=['内容完整性','引用准确性','严格字段一致性','规则符合性','人工修改量'];
  const state={scene:'document',resource:'files',agent:'directory',evaluation:'dimensions',deployment:'private',templateType:'requirements',templates:{},rules:[],feedback:[],members:{},runs:{},scores:{},connections:[],delivery:{},query:'',page:1};
  const sceneFields={document:[],quality:[['检查范围','完整性、引用与严格字段'],['材料版本','当前选定资料版本']],regulation:[['比较对象','所选法规的历史版与当前版'],['影响范围','当前项目及关联模板']],report:[['报告主题','产品资料综述'],['目标读者','注册与研发团队']],translation:[['源语言','中文'],['目标语言','英语'],['术语要求','保留产品名称、型号及数字']],market:[['目标市场','中国'],['分析范围','产品分类、公开竞品与市场准入']],question:[['问题','所选资料包含哪些产品要求？'],['回答要求','提供文件、页码与图片位置']]};
  const finalExamples={scores:[[84,96],[81,95],[86,98],[83,96],[72,91]],connection:{name:'企业推理服务',mode:'企业私有模型',model:'注册文档模型',endpoint:'https://model.internal.example/v1',scope:'当前企业核准资料',enabled:true},feedback:{name:'同一型号的单位表达需要保持一致',source:'产品技术要求 · 文稿 v1',target:'检查规则',status:'已发布',history:['已确认：纳入严格字段一致性检查','已纳入修订：增加单位与适用条件核对','已完成评测：相同样本逐维度复核','已发布：新版本采用更新后的检查规则']}};
  finalExamples.scores.forEach(([a,c],i)=>{state.scores[`D-1.4:${i}`]=a;state.scores[`D-1.5:${i}`]=c});state.connections.push({...finalExamples.connection});state.feedback.push(structuredClone(finalExamples.feedback));
  state.baseline='D-1.4';state.candidate='D-1.5';state.sampleScores={};
  state.ruleCatalog=baseRules().map(r=>({...r,status:'已发布',version:1}));
  const current=()=>ui.context();
  const project=()=>current().project;
  const task=()=>current().task;
  const ownFiles=()=>ui.materials.filter(m=>!m.deleted&&m.status!=='disabled'&&(m.scope==='company'||m.projectId===project().id));
  const fact=(rows)=>`<div class="final-facts">${rows.map(([label,value])=>`<div><span>${e(label)}</span><strong>${e(value)}</strong></div>`).join('')}</div>`;
  const select=(label,name,items,value)=>`<label class="field">${e(label)}<select name="${name}">${options(items,value)}</select></label>`;
  const card=(heading,body,actions='')=>`<section class="card pad final-card"><div class="panel-top"><h2>${e(heading)}</h2><div class="actions">${actions}</div></div>${body}</section>`;
  const refresh=()=>current().screen==='S16'?enhance('S16',document.getElementById('S16')):ui.refresh();
  const modal=(heading,body,confirm='关闭',submit=null)=>ui.openDialog(heading,body,confirm,submit);
  const record=(kind,detail)=>ui.record(kind,task().id,detail);
  function template(type){
    if(!state.templates[type])state.templates[type]={version:1,status:'已发布',rows:ui.chapterNames(type).map(name=>({name,method:/型号|指标|性能|日期|编号|标识/.test(name)?strategies[3]:/条款|原则|符号|术语/.test(name)?strategies[1]:/组成|附件|人员|制造|上市/.test(name)?strategies[0]:strategies[2],required:true,sources:[],rule:/型号|指标|性能/.test(name)?'核对指定资料的原值、单位、条件与型号':'内容完整、来源版本有效且能够定位原文',scope:'当前产品与申请路径',owner:project().owner})),history:[]};
    return state.templates[type];
  }
  function run(t=task()){
    if(!state.runs[t.id])state.runs[t.id]={stage:t.status==='已签发'?'complete':'review',step:t.status==='已签发'?5:4,issues:[],decisions:[],outcome:'',progress:[],input:null};
    return state.runs[t.id];
  }
  function sourceRows(ids){return ids.map(id=>{const m=ui.materials.find(x=>x.id===id);if(!m)throw new Error('资料不存在');return `<tr><td>${e(m.name)}</td><td>${e(m.version)}</td><td>${e(m.location)}</td><td>${b('查看原件','material-open',id)}</td></tr>`})}
  const sceneDetails={
    document:{description:'从产品资料到注册文稿，让每个章节都有依据。',abilities:['按文档类型与适用模板组织章节','关联指定资料、法规版本和图文引用','核验严格字段，支持人工审查与签发']},
    quality:{description:'定位材料缺项、引用问题与字段冲突。',abilities:['检查章节和附件的完整性','定位引用版本、型号及单位不一致','记录处理依据并跟踪复查结果']},
    regulation:{description:'读懂条款变化，明确项目需要调整的内容。',abilities:['并排比较法规与标准的不同版本','关联变化条款与项目、模板和规则','记录适用性判断及修订决定']},
    report:{description:'围绕研究主题，组织资料与可追溯结论。',abilities:['按主题与目标读者组织报告结构','汇集指定资料中的信息与图表','保留来源附录及证据不足的范围']},
    translation:{description:'保持术语、参数与原意一致的专业翻译。',abilities:['生成双语对照并保留专有名称','核对数字、单位及条件限定','支持术语表与逐项人工复核']},
    market:{description:'从公开资料中整理产品、竞品与市场信息。',abilities:['按目标市场与产品范围组织研究','比较公开产品信息并标注出处','区分有证据的结论与待确认信息']},
    question:{description:'围绕文档、图片与表格提问，直接查看依据。',abilities:['理解当前选择的资料与问题范围','将回答关联到文件、版本和原文位置','延续上下文，进入原件或创建研究任务']}
  };
  Object.entries(sceneDetails).forEach(([id,detail])=>detail.asset=id);
  function sceneCards(){return `<div class="final-scenes">${scenes.map(([id,name])=>{const detail=sceneDetails[id];if(!detail)throw new Error('场景卡片配置不存在');return `<article class="final-scene"><div class="scene-visual"><img src="ui-delivery/assets/scenes/scene-${detail.asset}.png" width="1536" height="1024" alt="${e(name)}场景插画" decoding="async"></div><div class="scene-copy"><h3>${e(name)}</h3><p>${e(detail.description)}</p><div class="actions">${b('创建任务','final-scene',id,true)}${b('能力说明','final-scene-info',id)}</div></div></article>`}).join('')}</div>`}
  function planRows(type){return table(['章节','处理方式','依据','检查要求','操作'],template(type).rows.map((row,index)=>`<tr><td><strong>${e(row.name)}</strong></td><td>${tag(row.method,'info')}</td><td>${row.sources.length?`${row.sources.length} 项固定资料`:e(row.scope)}</td><td>${e(row.rule)}</td><td>${b('配置','final-map',`${type}:${index}`)}</td></tr>`))}
  function setup(root){
    const area=root.querySelector('.setup-grid');if(!area)return;
    const existing=root.querySelector('h1')?.textContent==='任务配置',type=existing?task().type:root.querySelector('#document-type').value;
    const row=template(type);
    area.querySelector('.card:last-child').innerHTML=`<div class="panel-top"><h2>章节与生成规则</h2>${tag(`模板 v${row.version}`,'info')}</div>${planRows(type)}`;
    if(!existing){
      root.querySelector('.setup-grid>.card .form').insertAdjacentHTML('beforeend',`<div class="final-inputs">${select('场景 Agent','final-scene',scenes.map(s=>[s[0],s[1]]),state.scene)}${select('模板版本','final-template',[[`${type}:${row.version}`,`${ui.names[type]} · v${row.version}`]],`${type}:${row.version}`)}${select('法规与标准版本','final-regulations',ownFiles().filter(m=>m.category==='regulations').map(m=>[m.id,`${m.name} · ${m.version}`]),'')}<label class="field">本次目标<textarea name="final-goal" placeholder="填写用途、交付范围和重点要求"></textarea></label></div>`);
      root.querySelector('[data-action="create-task"]').dataset.action='final-start';
      root.querySelector('[data-action="final-start"]').textContent='确认依据并创建任务';
      root.querySelectorAll('[name="final-scene"] option').forEach(option=>option.disabled=option.value!=='document');
      root.querySelector('.final-inputs').insertAdjacentHTML('beforeend',`<div id="final-scene-fields">${(sceneFields[state.scene]||[]).map(([label,value],i)=>field(label,`scene-${i}`,value,'text','required')).join('')}</div>`);
      root.querySelector('[name="final-scene"]').addEventListener('change',event=>{state.scene=event.target.value;root.querySelector('#final-scene-fields').innerHTML=(sceneFields[state.scene]||[]).map(([label,value],i)=>field(label,`scene-${i}`,value,'text','required')).join('')});
      root.querySelectorAll('details,.requirement-row').forEach(n=>n.remove());
      area.querySelector('.card').insertAdjacentHTML('beforeend',card('输入依据',fact([['所属项目',project().name],['目标文档',ui.names[type]],['模板版本',`v${row.version}`],['资料选择','当前资料范围中的固定版本']]),b('核对全部依据','final-input')));
    }else{
      area.querySelector('.card').insertAdjacentHTML('beforeend',`<div class="actions final-space">${b('输入依据','final-input')}${b('执行与成果','final-run')}${b('质量检查','final-check')}</div>`);
    }
  }
  function renderResources(root){
    const heading=root.querySelector('.heading');
    heading.insertAdjacentHTML('afterend',tabs([['files','资料与资料集'],['templates','模板中心'],['rules','检查规则'],['regulations','法规与影响']],state.resource,'final-resource','资料管理分区'));
    if(state.resource==='files')return;
    const controls=[...root.children];controls.filter(n=>n!==heading&&!n.classList.contains('shared-tabs')).forEach(n=>n.remove());
    if(state.resource==='templates'){
      root.insertAdjacentHTML('beforeend',card('文档模板',`<div class="final-template-layout"><nav aria-label="模板类型">${Object.entries(ui.names).map(([id,name])=>b(name,'final-template-type',id,id===state.templateType)).join('')}</nav><div><div class="panel-top"><h3>${e(ui.names[state.templateType])}</h3>${tag(template(state.templateType).status,template(state.templateType).status==='已发布'?'ok':'warn')}</div>${planRows(state.templateType)}<div class="actions final-space">${b('适用范围','final-template-scope')}${b('版本记录','final-template-history')}${b('审核与发布','final-template-publish','',true)}</div></div></div>`));
    }
    if(state.resource==='rules')root.insertAdjacentHTML('beforeend',card('检查规则',table(['规则','检查对象','条件与处置','状态','操作'],state.ruleCatalog.map((r,index)=>`<tr><td>${e(r.name)}<small>v${r.version}</small></td><td>${e(r.dimension)}</td><td>${e(r.condition)}</td><td>${tag(r.status,r.status==='已发布'?'ok':'warn')}</td><td>${b('查看规则','final-rule',index)}${r.status==='待审核'?b('审核发布','final-rule-publish',index):''}</td></tr>`)),b('新增规则','final-rule-new','',true)));
    if(state.resource==='regulations')root.insertAdjacentHTML('beforeend',card('法规版本与项目影响',table(['法规或标准','当前版本','状态','操作'],ownFiles().filter(m=>m.category==='regulations').map(m=>`<tr><td>${e(m.name)}</td><td>${e(m.version)}</td><td>${tag(m.status==='approved'?'已核准':'待核准',m.status==='approved'?'ok':'warn')}</td><td>${b('版本比较','final-regulation',m.id)}${b('影响分析','final-impact',m.id)}</td></tr>`)),b('登记法规','upload-material')));
  }
  function baseRules(){return [{name:'章节完整性',dimension:'内容完整性',condition:'必需章节有正文或经确认的不适用依据',level:'阻止签发'},{name:'引用可定位',dimension:'引用准确性',condition:'引用保留文件、版本及原文位置',level:'阻止签发'},{name:'型号与参数',dimension:'严格字段一致性',condition:'型号、单位、条件与指定权威资料逐项核验',level:'人工确认'},{name:'模板适用性',dimension:'规则符合性',condition:'模板适用于产品属性与当前申请路径',level:'人工确认'}]}
  function getIssues(t=task()){
    const r=run(t);if(r.issues.length&&r.checkedRevision===t.revision)return r.issues;r.checkedRevision=t.revision;
    r.issues=t.modules.flatMap((m,index)=>{
      const issues=[];
      if(!m.text.trim())issues.push({chapter:index,name:'正文待补充',dimension:'内容完整性',detail:'当前章节没有正文',status:'待处理'});
      if(!m.refs?.length)issues.push({chapter:index,name:'引用待补充',dimension:'引用准确性',detail:'当前章节没有关联资料',status:'待处理'});
      if(m.refs?.some(ref=>!ref.checked)||t.verified[index]!==t.revision)issues.push({chapter:index,name:'来源待核验',dimension:'引用准确性',detail:'核对固定版本、原文位置和适用范围',status:'待处理'});
      if(template(t.type).rows[index]?.method==='严格字段核验')issues.push({chapter:index,name:'严格字段人工确认',dimension:'严格字段一致性',detail:'逐项确认原值、单位、适用条件与型号',status:'待处理'});
      if(index===0)issues.push({chapter:index,name:'模板适用性人工确认',dimension:'规则符合性',detail:'核对产品属性、申请路径、法规版本和模板范围',status:'待处理'});
      return issues;
    });return r.issues;
  }
  function quality(){
    const r=run(),issues=getIssues(),pending=issues.filter(i=>i.status==='待处理').length;
    modal('质量检查',`${fact([['文稿',task().name],['文稿版本',`v${task().revision}`],['检查范围',`${task().modules.length} 个章节`],['待处理',`${pending} 项`]])}<div class="final-dimensions">${dimensions.slice(0,4).map(d=>`<div><strong>${d}</strong><span>${issues.filter(i=>i.dimension===d&&i.status==='待处理').length} 项待处理</span></div>`).join('')}</div>${table(['检查结果','分类','位置','处理状态','操作'],issues.map((issue,index)=>`<tr><td>${e(issue.name)}<small>${e(issue.detail)}</small></td><td>${e(issue.dimension)}</td><td>${e(task().modules[issue.chapter].name)}</td><td>${tag(issue.status,issue.status==='已复核'?'ok':'warn')}</td><td>${b('定位正文','final-locate',issue.chapter)}${b('记录处理','final-resolve',index)}</td></tr>`))}<div class="actions final-space">${b('重新检查','final-recheck')}${b('查看检查依据','final-check-basis')}${b('提交审查','final-to-review','',true,pending>0)}</div>`);
  }
  function runView(){
    const r=run(),stages=['依据确认','资料提取','章节编写','规则检查','人工审查','成果整理'];
    modal('任务执行',`<div class="final-run-heading"><div><h3>${e(task().name)}</h3><p>${e(project().name)}</p></div>${tag(r.stage==='complete'?'成果已准备':r.stage==='paused'?'已暂停':'处理中',r.stage==='complete'?'ok':'info')}</div><ol class="final-run-steps">${stages.map((name,index)=>`<li class="${index<=r.step?'done':''}"><span>${index+1}</span><div><strong>${name}</strong><small>${index<r.step?'已完成':index===r.step?'当前阶段':'等待前序'}</small></div></li>`).join('')}</ol>${fact([['输入版本',`v${r.input?.version||1}`],['Agent',task().agent],['已准备章节',`${task().modules.filter(m=>m.text.trim()).length} / ${task().modules.length}`],['当前成果',`文稿 v${task().revision}`]])}<div class="actions final-space">${b(r.stage==='paused'?'继续任务':'暂停任务','final-pause')}${b('查看正文','final-read')}${b('质量检查','final-check')}${b('成果清单','final-deliveries','',true)}</div>`);
  }
  function agentPage(root){
    if(state.agent==='configuration'){root.innerHTML=ui.configPage();root.querySelector('.heading').insertAdjacentHTML('afterend',`<div class="actions">${b('返回场景管理','final-agent-area','directory')}</div>`);return}
    root.innerHTML=WorkspaceUI.title('Agent 管理',b('创建 Agent','final-agent-new','',true))+tabs([['directory','场景与版本'],['models','模型连接'],['capability','专业角色与工具'],['publish','评测与发布']],state.agent,'final-agent-area','Agent 管理分区');
    if(state.agent==='directory')root.insertAdjacentHTML('beforeend',card('场景 Agent',table(['场景','输入','产出','版本','操作'],scenes.map(([id,name,input,output])=>`<tr><td><strong>${name}</strong></td><td>${input}</td><td>${output}</td><td>${tag(id==='document'?(state.release||'D-1.4'):'v1.0','info')}</td><td>${b('配置','final-agent-config',id)}${b('创建任务','final-scene',id)}</td></tr>`))));
    if(state.agent==='models')root.insertAdjacentHTML('beforeend',card('模型连接',`${fact([['部署方式',state.deployment==='private'?'企业私有化':'企业云空间'],['连接用途','规划、编写、核验、问答'],['资料策略','任务范围与连接权限共同校验']])}${state.connections.length?table(['连接','部署','模型','状态','操作'],state.connections.map((c,index)=>`<tr><td>${e(c.name)}</td><td>${e(c.mode)}</td><td>${e(c.model)}</td><td>${tag(c.enabled?'已启用':'已停用',c.enabled?'ok':'neutral')}</td><td>${b('详情','final-model-detail',index)}${b(c.enabled?'停用':'启用','final-model-toggle',index)}</td></tr>`)):'<div class="final-model-options">'+b('配置企业私有模型','final-model-new','private',true)+b('配置外部 API','final-model-new','api')+'</div>'}`,b('新增连接','final-model-new','api',true)));
    if(state.agent==='capability')root.insertAdjacentHTML('beforeend',card('专业角色与工具',`<div class="final-role-grid">${['任务规划','资料研究','严格字段核验','章节编写','来源与规则审查','成果整理'].map((name,index)=>`<article><h3>${name}</h3><p>${['确认目标与章节依据','限定资料范围并定位引用','核对原值、单位与条件','按章节策略准备文稿','检查完整性与证据支持','整理正文、目录与附件'][index]}</p>${tag('独立权限','info')}${b('配置职责','final-role',index)}</article>`).join('')}</div>`));
    if(state.agent==='publish')root.insertAdjacentHTML('beforeend',card('发布检查',table(['检查项','要求','操作'],[['版本输入','固定模板、规则、模型和工具版本','final-agent-config'],['相同样本评测','按维度比较质量与人工处理','final-evaluation'],['审核决定','记录责任人和发布范围','final-release']].map(([name,requirement,action])=>`<tr><td>${name}</td><td>${requirement}</td><td>${b('查看',action,'document')}</td></tr>`)),b('查看版本记录','final-release-history')));
    root.insertAdjacentHTML('beforeend',`<div class="actions final-space">${b('版本、Skill 与接口配置','final-agent-area','configuration')}</div>`);
  }
  function evaluationPage(root){
    root.innerHTML=WorkspaceUI.title('评测与运营')+tabs([['dimensions','质量评测'],['feedback','反馈改进'],['usage','用量与成本'],['audit','审计记录']],state.evaluation,'final-eval-area','评测与运营分区');
    if(state.evaluation==='dimensions'){
      root.insertAdjacentHTML('beforeend',card('相同样本版本比较',`<div class="form two">${select('基准版本','final-baseline',ui.versions.map(v=>[v.id,v.id]),state.baseline)}${select('候选版本','final-candidate',ui.versions.map(v=>[v.id,v.id]),state.candidate)}</div>${table(['维度','基准版本','候选版本','变化'],dimensions.map((d,i)=>{const a=state.scores[`${state.baseline}:${i}`],c=state.scores[`${state.candidate}:${i}`];return `<tr><td>${d}</td><td>${a??'待录入'}</td><td>${c??'待录入'}</td><td>${a===undefined||c===undefined?'等待评分':c-a}</td></tr>`}))}<div class="actions final-space">${b('样本与版本输出','final-samples')}${b('逐维度评分','final-score','',true)}${b('发布决定','final-release')}</div>`));
      for(const key of ['baseline','candidate'])root.querySelector(`[name="final-${key}"]`).addEventListener('change',event=>{state[key]=event.target.value;refresh()});
    }
    if(state.evaluation==='feedback')root.insertAdjacentHTML('beforeend',card('反馈与版本改进',state.feedback.length?table(['问题','来源','处理对象','进度','操作'],state.feedback.map((f,i)=>`<tr><td>${e(f.name)}</td><td>${e(f.source)}</td><td>${e(f.target)}</td><td>${tag(f.status,'info')}</td><td>${b('处理记录','final-feedback-detail',i)}</td></tr>`)):'<div class="empty-message"><strong>当前没有待处理反馈</strong>文档批注与退回意见可以提交到改进记录。</div>',b('登记反馈','final-feedback-new','',true)));
    if(state.evaluation==='usage'){
      state.usageScope||='project';state.usagePeriod||='month';
      const factor=(state.usageScope==='enterprise'?4:1)*(state.usagePeriod==='week'?.25:1),input=1240000*factor,output=310000*factor,cost=72.54*factor;
      root.insertAdjacentHTML('beforeend',card('用量与成本',`<div class="form two">${select('统计范围','usage-scope',[['project','当前项目'],['enterprise','当前企业'],['agent','注册文档 Agent']],state.usageScope)}${select('统计周期','usage-period',[['month','本月'],['week','本周']],state.usagePeriod)}</div>${fact([['已完成任务',Math.round(48*factor)],['结果采纳率','91.7%'],['退回率','8.3%'],['平均人工评分','4.6 / 5']])}${table(['调用用途','输入 Token','输出 Token','费用','操作'],[['资料研究',.4],['文档编写',.45],['核验与问答',.15]].map(([name,ratio])=>`<tr><td>${name}</td><td>${Math.round(input*ratio).toLocaleString()}</td><td>${Math.round(output*ratio).toLocaleString()}</td><td>¥${(cost*ratio).toFixed(2)}</td><td>${b('调用记录','final-usage-log',name)}</td></tr>`))}<p>合计 ¥${cost.toFixed(2)} · 输入 ${input.toLocaleString()} Token · 输出 ${output.toLocaleString()} Token</p>`,b('预算与计费规则','final-budget')));
      for(const [name,key] of [['usage-scope','usageScope'],['usage-period','usagePeriod']])root.querySelector(`[name="${name}"]`).addEventListener('change',event=>{state[key]=event.target.value;refresh()});
    }
    if(state.evaluation==='audit')root.insertAdjacentHTML('beforeend',card('操作审计',table(['时间','人员','操作','对象'],ui.events.map(event=>`<tr><td>${e(event.time)}</td><td>${e(event.person)}</td><td>${e(event.action)}</td><td>${e(event.detail)}</td></tr>`))));
  }
  function enhance(id,root){
    if(id==='S01'){
      const side=root.querySelector('.work-side');if(side)side.querySelector('.card').remove();root.querySelector('.work-metrics').insertAdjacentHTML('afterend',card('场景 Agent',sceneCards(),b('管理场景','navigate','S13')));
    }
    if(id==='S18')root.querySelector('.project-toolbar>.actions').insertAdjacentHTML('afterbegin',b('成员与权限','final-members'));
    if(id==='S07')setup(root);
    if(id==='S04')renderResources(root);
    if(id==='S13')agentPage(root);
    if(id==='S15')evaluationPage(root);
    if(id==='S16')root.innerHTML=WorkspaceUI.title('企业与连接')+card('部署与企业服务',`${tabs([['private','私有化部署'],['saas','SaaS 企业空间'],['service','配套服务协作']],state.deployment,'final-deployment','部署方式')}${fact([['当前企业',project().company||project().name],['身份接入','企业账号与单点登录'],['数据边界','企业与项目分别授权']])}<div class="final-role-grid">${[['账号与单点登录','身份提供方、角色映射与会话策略'],['资料系统连接','文件同步、版本、访问权限与更新策略'],['专家服务协作','服务项目、专家范围、交付文档与验收']].map(([name,description],i)=>`<article><h3>${name}</h3><p>${description}</p>${b('管理连接','final-enterprise',i)}</article>`).join('')}</div>`);
    if(id==='S09'){
      root.querySelector('.heading>.actions').insertAdjacentHTML('afterbegin',b('质量检查','final-check'));
      const operations=root.querySelector('.reader-operations');if(operations)operations.innerHTML='<summary>执行与成果</summary>'+fact([['当前阶段',run().stage==='complete'?'成果已准备':'章节处理'],['文稿版本',`v${task().revision}`]])+b('执行进度','final-run')+b('成果清单','final-deliveries');
    }
    if(id==='S11')root.querySelector('.heading>.actions').insertAdjacentHTML('afterbegin',b('成果清单','final-deliveries'));
  }
  function inputView(){
    const type=current().screen==='S07'&&!current().setupTaskId?current().newType:task().type,r=run(),input=r.input;
    const sources=input?.sources||ui.selectedSources().map(m=>m.id);
    modal('本次任务依据',`${fact([['项目',project().name],['文档',ui.names[type]||task().name],['模板',`${ui.names[type]||task().name} v${template(type).version}`],['Agent',task().agent],['目标',input?.goal||'按适用模板准备文稿并完成检查']])}${table(['资料','固定版本','原文位置','操作'],sourceRows(sources))}${planRows(type)}`);
  }
  function handle(action,value){
    if(!action.startsWith('final-')){if(['task-open','material-open','navigate'].includes(action)&&document.querySelector('#product-dialog').open)ui.closeDialog();return false}
    if(action==='final-scene'){if(value!=='document')return true;state.scene=value;if(document.querySelector('#product-dialog').open)ui.closeDialog();ui.navigate('S07');return true}
    if(action==='final-scene-result'){sceneResult(value);return true}
    if(action==='final-scene-info'){const scene=scenes.find(item=>item[0]===value),detail=sceneDetails[value];if(!scene||!detail)throw new Error('场景能力不存在');modal(`${scene[1]} · 能力说明`,`<div class="scene-capability"><img src="ui-delivery/assets/scenes/scene-${detail.asset}.png" width="1536" height="1024" alt="${e(scene[1])}场景插画"><div><p class="scene-purpose">${e(detail.description)}</p><h3>可以完成的工作</h3><ul>${detail.abilities.map(text=>`<li>${e(text)}</li>`).join('')}</ul></div></div>${fact([['需要提供',scene[2]],['交付内容',scene[3]]])}<div class="actions final-space">${b('创建任务','final-scene',value,true)}</div>`);return true}
    if(action==='final-scene-export'){const r=SceneResults[value];if(!r)throw new Error('场景成果不存在');ui.download(`${r.title}.html`,`<!doctype html><html lang="zh-CN"><meta charset="utf-8"><title>${e(r.title)}</title><body><h1>${e(r.title)}</h1><p>${e(r.summary)}</p>${table(r.columns,r.rows.map(row=>`<tr>${row.map(c=>`<td>${e(c)}</td>`).join('')}</tr>`))}${r.sections.map(([heading,text])=>`<h2>${e(heading)}</h2><p>${e(text)}</p>`).join('')}</body></html>`);record('导出场景成果',r.title);return true}
    if(action==='final-resource'){state.resource=value;refresh();return true}
    if(action==='final-template-type'){state.templateType=value;refresh();return true}
    if(action==='final-agent-area'){state.agent=value;refresh();return true}
    if(action==='final-eval-area'){state.evaluation=value;refresh();return true}
    if(action==='final-deployment'){state.deployment=value;refresh();return true}
    if(action==='final-input'){inputView();return true}
    if(action==='final-start'){
      const root=document.getElementById('S07'),type=root.querySelector('#document-type').value,scene=root.querySelector('[name="final-scene"]').value,goal=root.querySelector('[name="final-goal"]').value.trim(),sources=ui.selectedSources().map(m=>m.id),regulation=root.querySelector('[name="final-regulations"]').value;
      if(scene!=='document')return true;
      if(template(type).status!=='已发布'){modal('模板等待审核','<p>当前模板存在未发布修改，请完成审核后创建任务。</p>'+b('进入模板中心','final-go-templates',type));return true}
      modal('确认任务依据',`${fact([['项目',project().name],['场景',scenes.find(s=>s[0]===scene)[1]],['目标文档',ui.names[type]],['模板',`v${template(type).version}`],['固定资料',`${sources.length} 项`],['目标',goal||'按模板准备文稿并检查']])}<label><input type="checkbox" name="confirm" required> 已确认适用范围与输入版本</label>`,'创建任务',()=>{
        const parameters=(sceneFields[scene]||[]).map(([label],i)=>[label,root.querySelector(`[name="scene-${i}"]`).value]);ui.closeDialog();ui.create(type,template(type));const r=run();r.input={version:1,scene,goal,sources,regulation,parameters,template:structuredClone(template(type))};r.step=2;r.stage='prepared';r.progress=['依据已确认','资料已关联'];record('确认任务依据',ui.names[type]);refresh();runView();
      });return true;
    }
    if(action==='final-go-templates'){state.resource='templates';state.templateType=value;ui.closeDialog();ui.navigate('S04');return true}
    if(action==='final-map'){
      const [type,index]=value.split(':'),t=template(type),row=t.rows[Number(index)];
      modal(row.name,`<div class="form">${select('处理方式','method',strategies.map(s=>[s,s]),row.method)}<fieldset class="choice-list"><legend>指定资料与版本</legend>${ownFiles().map(m=>`<label><input name="sources" type="checkbox" value="${m.id}" ${row.sources.includes(m.id)?'checked':''}>${e(m.name)} · ${e(m.version)}</label>`).join('')}</fieldset>${field('适用条件','scope',row.scope,'text','required')}<label class="field">检查要求<textarea name="rule" required>${e(row.rule)}</textarea></label><label><input type="checkbox" name="required" ${row.required?'checked':''}> 必需章节</label></div>`,'保存章节规则',data=>{t.history.unshift(structuredClone(t.rows));Object.assign(row,{method:data.get('method'),sources:data.getAll('sources'),scope:data.get('scope').trim(),rule:data.get('rule').trim(),required:data.has('required')});t.version++;t.status='待审核';record('更新章节规则',row.name);ui.closeDialog();refresh()});return true;
    }
    if(action==='final-template-scope'){const t=template(state.templateType);modal('模板适用范围',`<div class="form">${field('产品属性','attributes',t.attributes||'依产品属性选择','text','required')}${select('申请路径','path',[['首次注册','首次注册'],['变更注册','变更注册'],['延续注册','延续注册']],t.path||'首次注册')}${field('核准责任人','owner',t.owner||project().owner,'text','required')}<label class="field">适用依据<textarea name="reason" required>${e(t.reason||'')}</textarea></label></div>`,'保存适用范围',data=>{Object.assign(t,Object.fromEntries(data));t.status='待审核';ui.closeDialog();refresh()});return true}
    if(action==='final-template-history'){const t=template(state.templateType);modal('模板版本记录',`<p>当前版本 v${t.version} · ${t.status}</p>${t.history.map((rows,index)=>`<details><summary>v${t.version-index-1}</summary>${rows.map(r=>`<p>${e(r.name)} · ${e(r.method)} · ${e(r.rule)}</p>`).join('')}</details>`).join('')}`);return true}
    if(action==='final-template-publish'){const t=template(state.templateType);modal('审核与发布模板',fact([['模板',ui.names[state.templateType]],['版本',t.version],['章节',t.rows.length]])+'<label class="field">审核意见<textarea name="reason" required></textarea></label><label><input name="confirm" type="checkbox" required> 已核对适用范围、章节与来源规则</label>','确认发布',data=>{t.status='已发布';t.reason=data.get('reason');record('发布模板',ui.names[state.templateType]);ui.closeDialog();refresh()});return true}
    if(action==='final-check'){quality();return true}
    if(action==='final-check-basis'){modal('检查依据',table(['规则','分类','检查要求','处置'],baseRules().map(r=>`<tr><td>${r.name}</td><td>${r.dimension}</td><td>${r.condition}</td><td>${r.level}</td></tr>`)));return true}
    if(action==='final-recheck'){run().issues=[];run().step=3;record('重新检查',`文稿 v${task().revision}`);quality();return true}
    if(action==='final-resolve'){const issue=run().issues[Number(value)];modal('处理检查问题',`<p>${e(issue.name)} · ${e(issue.detail)}</p>${b('定位并处理正文与来源','final-locate',issue.chapter)}<label class="field">处理依据<textarea name="reason" required></textarea></label>`,'记录并复查',data=>{const m=task().modules[issue.chapter];issue.reason=data.get('reason');issue.status=m.text.trim()&&m.refs?.length&&m.refs.every(r=>r.checked)&&task().verified[issue.chapter]===task().revision?'已复核':'待处理';run().decisions.push({...issue,revision:task().revision});record('处理检查问题',issue.name);quality()});return true}
    if(action==='final-locate'){ui.closeDialog();ui.read(Number(value));return true}
    if(action==='final-to-review'){ui.closeDialog();ui.navigate('S11');return true}
    if(action==='final-run'){runView();return true}
    if(action==='final-pause'){const r=run();if(r.stage==='paused')r.stage=r.resumeStage;else{r.resumeStage=r.stage;r.stage='paused'}record('任务状态变更',r.stage);runView();return true}
    if(action==='final-read'){ui.closeDialog();ui.navigate('S09');return true}
    if(action==='final-deliveries'){deliveries();return true}
    if(action==='final-download'){ui.download(`${task().name}-${value}.html`,artifactHtml(value));record('导出成果',`${task().name} · ${value}`);return true}
    if(action==='final-print'){const view=window.open('','_blank');if(!view)throw new Error('文档预览窗口未打开');view.document.open();view.document.write(artifactHtml('正文'));view.document.close();view.focus();view.print();return true}
    if(action==='final-archive'){modal('确认归档',fact([['项目',project().name],['文稿',task().name],['固定版本',`v${task().revision}`]])+'<label><input name="confirm" type="checkbox" required> 保留当前正文、来源与处理记录</label>','归档当前版本',()=>{run().stage='complete';run().step=5;state.delivery[task().id]='已归档';record('归档成果',task().name);deliveries()});return true}
    if(action==='final-members'){members();return true}
    if(action==='final-member-edit'){memberEdit(value);return true}
    if(action==='final-rule'||action==='final-rule-new'){ruleEdit(value,action==='final-rule-new');return true}
    if(action==='final-rule-publish'){const rule=state.ruleCatalog[Number(value)];modal('审核检查规则',fact([['规则',rule.name],['版本',rule.version],['处理方式',rule.level]])+'<label class="field">审核依据<textarea name="reason" required></textarea></label>','发布规则',data=>{rule.status='已发布';rule.reason=data.get('reason');record('发布检查规则',rule.name);ui.closeDialog();refresh()});return true}
    if(action==='final-regulation'||action==='final-impact'){regulation(value,action==='final-impact');return true}
    if(action==='final-evaluation'){ui.closeDialog();ui.navigate('S15');return true}
    if(action==='final-agent-config'){agentConfig(value);return true}
    if(action==='final-agent-new'){modal('创建场景 Agent',`${field('名称','name','','text','required')}${select('场景类型','scene',scenes.map(s=>[s[0],s[1]]),'document')}<label class="field">输入与产出<textarea name="io" required></textarea></label>`,'创建配置',data=>{const id=`custom-${scenes.length}`;sceneDetails[id]={...structuredClone(sceneDetails[data.get('scene')]),description:data.get('io')};scenes.push([id,data.get('name'),data.get('io'),'按任务定义交付']);ui.closeDialog();refresh()});return true}
    if(action==='final-role'){roleEdit(Number(value));return true}
    if(action==='final-score'){score();return true}
    if(action==='final-samples'){modal('评测样本与版本输出',table(['样本','文稿版本','章节','操作'],ui.tasks.map(t=>`<tr><td>${e(t.name)}</td><td>v${t.revision}</td><td>${t.modules.length}</td><td>${b('阅读文稿','task-open',t.id)}</td></tr>`)));return true}
    if(action==='final-feedback-new'){feedback();return true}
    if(action==='final-feedback-detail'){feedbackDetail(Number(value));return true}
    if(action==='final-release'||action==='final-release-history'){release(action.endsWith('history'));return true}
    if(action==='final-model-new'){modelEdit(value);return true}
    if(action==='final-model-toggle'){state.connections[Number(value)].enabled=!state.connections[Number(value)].enabled;refresh();return true}
    if(action==='final-model-detail'){const c=state.connections[Number(value)];modal('模型连接详情',fact(Object.entries(c).filter(([key])=>['name','mode','model','endpoint','scope'].includes(key)).map(([key,val])=>[{name:'名称',mode:'部署',model:'模型',endpoint:'地址',scope:'权限范围'}[key],val]))+`<div class="actions">${b('编辑配置','final-model-edit',value)}${b('凭据管理','final-model-secret',value)}${b('验证报告','final-model-report',value)}${b('调用限制','final-budget')}</div>`);return true}
    if(action==='final-model-edit'){modelEdit('edit',Number(value));return true}
    if(action==='final-model-secret'){modal('凭据管理','<label class="field">访问凭据<input name="secret" type="password" autocomplete="new-password" required></label><label class="field">更换原因<textarea name="reason" required></textarea></label>','更新凭据',data=>{record('更换连接凭据',state.connections[Number(value)].name);ui.closeDialog()});return true}
    if(action==='final-model-report'){modal('连接验证报告',table(['检查项','结果'],['地址与协议','模型可访问性','授权范围','调用限制'].map(name=>`<tr><td>${name}</td><td>${tag('通过','ok')}</td></tr>`))+b('查看连接配置','final-model-detail',value));return true}
    if(action==='final-budget'){const c=state.budget||{tokens:'120000',cost:'100',stop:'pause',owner:project().owner};modal('预算与计费规则',`${field('任务 Token 上限','tokens',c.tokens,'number','min="1" required')}${field('任务费用上限','cost',c.cost,'number','min="0" required')}${select('超限处理','stop',[['pause','暂停并申请追加'],['stop','停止新增调用']],c.stop)}<label class="field">授权责任人<input name="owner" required value="${e(c.owner)}"></label>`,'保存策略',data=>{state.budget=Object.fromEntries(data);record('更新预算策略',project().name);ui.closeDialog()});return true}
    if(action==='final-usage-log'){modal('调用与计费记录',table(['用途','模型','状态','输入 Token','输出 Token','费用'],[[value,'注册文档模型','已完成','12,000','3,000','¥0.702']].map(row=>`<tr>${row.map(x=>`<td>${e(x)}</td>`).join('')}</tr>`))+fact([['计费规则','输入 ¥12 / 百万 Token；输出 ¥186 / 百万 Token'],['资料范围','当前任务固定版本'],['超限策略',state.budget?.stop==='stop'?'停止新增调用':'暂停并申请追加']]));return true}
    if(action==='final-enterprise'){enterprise(Number(value));return true}
    throw new Error('目标设计操作不存在');
  }
  function deliveries(){modal('成果清单',`${fact([['文稿',task().name],['当前版本',`v${task().revision}`],['状态',state.delivery[task().id]||'成果已准备']])}${table(['文件','内容','版本','操作'],[['正文','章节、图文与正式引用'],['检查报告','检查范围、结果与处理依据'],['审查记录','批注、回复与版本决定'],['来源附录','资料名称、版本与原文位置']].map(([name,content])=>`<tr><td>${name}</td><td>${content}</td><td>v${task().revision}</td><td>${b('预览','final-preview',name)}${b('下载 HTML','final-download',name)}</td></tr>`))}<div class="actions final-space">${b('导出 PDF','final-print')}${b('归档当前成果','final-archive','',true)}${b('登记改进反馈','final-feedback-new')}</div>`)}
  function sceneResult(id){
    const result=SceneResults[id];if(!result)throw new Error('场景成果不存在');
    const body=`<article class="final-result"><div class="panel-top"><h2>${e(result.title)}</h2>${tag(result.status,'ok')}</div><p>${e(result.summary)}</p>${table(result.columns,result.rows.map(row=>`<tr>${row.map(cell=>`<td>${e(cell)}</td>`).join('')}</tr>`))}${result.sections.map(([heading,text])=>`<section><h3>${e(heading)}</h3><p>${e(text)}</p></section>`).join('')}</article>`;
    modal(result.title,body+`<div class="actions final-space">${b('创建同类任务','final-scene',id,true)}${b('导出成果','final-scene-export',id)}${['report','translation','market','question'].includes(id)?b('查看引用资料','material-open','public-remi'):b('查看模板与检查规则','final-go-templates','requirements')}</div>`);
  }
  function artifactHtml(kind){
    if(kind==='正文')return ui.documentHtml(task());
    const body=kind==='检查报告'?table(['问题','分类','结果','处理依据'],getIssues().map(i=>`<tr><td>${e(i.name)}</td><td>${e(i.dimension)}</td><td>${e(i.status)}</td><td>${e(i.reason||'待处理')}</td></tr>`)):kind==='审查记录'?task().comments.map(c=>`<blockquote>${e(c.quote)}</blockquote><p>${e(c.text)}</p><p>${e(c.reply||'待处理')}</p>`).join(''):table(['资料','版本','原文位置'],task().modules.flatMap(m=>(m.refs||[]).map(r=>`<tr><td>${e(r.name)}</td><td>${e(r.version)}</td><td>${e(r.location)}</td></tr>`)));
    return `<!doctype html><html lang="zh-CN"><meta charset="utf-8"><title>${e(kind)}</title><style>body{font:15px/1.8 sans-serif;padding:32px;color:#243247}table{border-collapse:collapse;width:100%}td,th{padding:12px;border:1px solid #dce3ec;text-align:left}blockquote{border-left:3px solid #1768c4;padding:12px}h1{font-size:24px}</style><h1>${e(task().name)} · ${e(kind)}</h1><p>文稿 v${task().revision}</p>${body}</html>`;
  }
  function members(){
    const list=state.members[project().id]||(state.members[project().id]=[{name:project().owner,role:'项目负责人',scope:'项目全部资料',read:true,write:true,review:true,sign:false},{name:'周予安',role:'研发人员',scope:'研发资料与补证任务',read:true,write:true,review:false,sign:false},{name:'沈知微',role:'知识审核人员',scope:'法规、模板与规则',read:true,write:false,review:true,sign:false}]);
    modal('项目成员与资料权限',table(['成员','角色','资料范围','读取','编辑','审核','签发','操作'],list.map((m,i)=>`<tr><td>${e(m.name)}</td><td>${e(m.role)}</td><td>${e(m.scope)}</td>${['read','write','review','sign'].map(k=>`<td>${m[k]?'允许':'未授权'}</td>`).join('')}<td>${b('调整权限','final-member-edit',i)}</td></tr>`))+b('添加成员','final-member-edit','new'));
  }
  function memberEdit(id){const list=state.members[project().id],m=id==='new'?{name:'',role:'协作人员',scope:'指定资料'}:list[Number(id)];modal('项目授权',`${field('成员','name',m.name,'text','required')}${select('岗位','role',['项目负责人','注册人员','研发人员','知识审核人员','签发人员','外部专家'].map(x=>[x,x]),m.role)}${field('资料范围','scope',m.scope,'text','required')}<fieldset class="choice-list"><legend>允许动作</legend>${[['read','读取'],['write','编辑'],['review','审核'],['sign','签发']].map(([key,label])=>`<label><input name="${key}" type="checkbox" ${m[key]?'checked':''}>${label}</label>`).join('')}</fieldset><label class="field">授权依据<textarea name="reason" required></textarea></label>`,'保存授权',data=>{Object.assign(m,{name:data.get('name'),role:data.get('role'),scope:data.get('scope'),reason:data.get('reason')});['read','write','review','sign'].forEach(k=>m[k]=data.has(k));if(id==='new')list.push(m);record('更新项目授权',m.name);members()})}
  function ruleEdit(id,fresh){const r=fresh?{name:'',dimension:dimensions[0],condition:'',level:'人工确认',version:0}:state.ruleCatalog[Number(id)];modal('检查规则',`${field('规则名称','name',r.name,'text','required')}${select('检查维度','dimension',dimensions.map(x=>[x,x]),r.dimension)}<label class="field">判断条件<textarea name="condition" required>${e(r.condition)}</textarea></label>${select('处置方式','level',['阻止签发','人工确认','提示'].map(x=>[x,x]),r.level)}`,'保存待审核规则',data=>{const updated={...Object.fromEntries(data),status:'待审核',version:r.version+1};if(fresh)state.ruleCatalog.push(updated);else state.ruleCatalog[Number(id)]=updated;record('提交规则审核',updated.name);ui.closeDialog();refresh()})}
  function regulation(id,impact){const m=ui.materials.find(x=>x.id===id);if(!m)throw new Error('法规不存在');const related=ui.tasks.filter(t=>t.modules.some(module=>module.sourceId===id||module.refs?.some(r=>r.id===id)));modal(impact?'法规影响分析':'法规版本比较',fact([['文件',m.name],['版本',m.version],['引用任务',related.length]])+(impact?table(['受影响任务','版本','处理状态','操作'],related.map(t=>`<tr><td>${e(t.name)}</td><td>v${t.revision}</td><td>待核查适用性</td><td>${b('打开任务','task-open',t.id)}</td></tr>`)):`<div class="final-comparison">${m.versions.slice(-2).map(v=>`<article><h3>${e(v.version)}</h3><p>${e(v.text)}</p></article>`).join('')}</div>`)+`<label class="field">变化解读与处理决定<textarea name="decision" required></textarea></label>`,'保存影响决定',data=>{record('法规影响决定',`${m.name}：${data.get('decision')}`);ui.closeDialog()})}
  function agentConfig(id){const s=scenes.find(s=>s[0]===id)||scenes[0],c=state[`agent:${id}`]||{goal:s[2],document:'requirements',source:'project',owner:project().owner,condition:'目标内容完整，来源能够定位，待确认问题已经处理。'};modal(`${s[1]} · 配置`,`${fact([['输入',s[2]],['产出',s[3]]])}<div class="form two">${field('任务目标','goal',c.goal,'text','required')}${select('默认文档','document',Object.entries(ui.names),c.document)}${select('来源策略','source',[['project','项目核准资料'],['hybrid','项目资料与指定公开来源']],c.source)}${field('责任人','owner',c.owner,'text','required')}</div><label class="field">完成条件<textarea name="condition" required>${e(c.condition)}</textarea></label>`,'保存配置版本',data=>{state[`agent:${id}`]=Object.fromEntries(data);record('更新 Agent 配置',s[1]);ui.closeDialog();refresh()})}
  function roleEdit(index){const names=['任务规划','资料研究','严格字段核验','章节编写','来源与规则审查','成果整理'],c=state[`role:${index}`]||{duty:names[index],model:'private',tools:['资料读取'],stop:'资料存在冲突、必要证据缺少或达到预算上限时暂停，并请任务负责人处理。'};modal(names[index],`${field('职责','duty',c.duty,'text','required')}${select('模型连接','model',[['private','企业私有模型'],['api','授权外部模型']],c.model)}<fieldset class="choice-list"><legend>工具权限</legend>${['资料读取','限定检索','字段提取','文档编辑','规则检查','文稿导出'].map(name=>`<label><input name="tools" type="checkbox" value="${name}" ${c.tools.includes(name)?'checked':''}>${name}</label>`).join('')}</fieldset><label class="field">停止与人工确认条件<textarea name="stop" required>${e(c.stop)}</textarea></label>`,'保存职责',data=>{state[`role:${index}`]={...Object.fromEntries(data),tools:data.getAll('tools')};record('更新角色职责',names[index]);ui.closeDialog()})}
  function score(){modal('逐维度评测',`${select('配置版本','version',ui.versions.map(v=>[v.id,v.id]),state.candidate)}${select('样本','sample',ui.tasks.map(t=>[t.id,t.name]),task().id)}${dimensions.map((name,i)=>field(name,`score-${i}`,'','number','min="0" max="100" required')).join('')}<label class="field">评分依据<textarea name="reason" required></textarea></label>`,'保存评分',data=>{const version=data.get('version'),sample=data.get('sample');state.sampleScores[`${version}:${sample}`]={version,sample,values:dimensions.map((d,i)=>Number(data.get(`score-${i}`))),reason:data.get('reason')};const rows=Object.values(state.sampleScores).filter(r=>r.version===version);dimensions.forEach((d,i)=>state.scores[`${version}:${i}`]=Math.round(rows.reduce((sum,r)=>sum+r.values[i],0)/rows.length));record('保存维度评测',`${version} · ${sample} · ${data.get('reason')}`);ui.closeDialog();refresh()});const form=document.querySelector('#product-dialog');const fill=()=>{const row=state.sampleScores[`${form.querySelector('[name="version"]').value}:${form.querySelector('[name="sample"]').value}`];dimensions.forEach((d,i)=>form.querySelector(`[name="score-${i}"]`).value=row?row.values[i]:'');form.querySelector('[name="reason"]').value=row?.reason||''};form.querySelector('[name="version"]').addEventListener('change',fill);form.querySelector('[name="sample"]').addEventListener('change',fill);fill()}
  function feedback(){modal('登记改进反馈',`${field('问题','name','','text','required')}${select('关联对象','target',[['模板','模板'],['检查规则','检查规则'],['Agent','Agent'],['资料','资料']],'检查规则')}<label class="field">问题依据<textarea name="reason" required></textarea></label>`,'登记反馈',data=>{state.feedback.push({...Object.fromEntries(data),source:`${task().name} v${task().revision}`,status:'待审核',history:[]});record('登记改进反馈',data.get('name'));ui.closeDialog();state.evaluation='feedback';ui.navigate('S15')})}
  function feedbackDetail(index){const f=state.feedback[index];modal('反馈处理记录',fact([['问题',f.name],['来源',f.source],['处理对象',f.target],['状态',f.status]])+f.history.map(h=>`<p>${e(h)}</p>`).join('')+select('处理阶段','status',['待审核','已确认','已纳入修订','已完成评测','已发布'].map(x=>[x,x]),f.status)+'<label class="field">决定依据<textarea name="reason" required></textarea></label>','保存处理决定',data=>{f.status=data.get('status');f.history.push(`${f.status}：${data.get('reason')}`);record('处理改进反馈',f.name);ui.closeDialog();refresh()})}
  function release(history){if(history){modal('配置发布记录',table(['版本','状态','说明'],ui.versions.map(v=>`<tr><td>${v.id}</td><td>${v.status}</td><td>${e(v.note)}</td></tr>`)));return}modal('发布决定',select('版本','version',ui.versions.map(v=>[v.id,v.id]),state.candidate)+'<label class="field">评测结论与发布依据<textarea name="reason" required></textarea></label><label><input type="checkbox" name="confirm" required> 已核对样本输出、各维度评测与权限范围</label>','确认发布',data=>{const version=data.get('version');if(dimensions.some((d,i)=>state.scores[`${version}:${i}`]===undefined)){ui.error('请完成全部维度评测');return}record('发布配置决定',`${version}：${data.get('reason')}`);state.release=version;Object.assign(ui.versions.find(v=>v.id===version),{status:'已发布',checked:true,note:data.get('reason'),published:new Date().toLocaleDateString('sv-SE')});ui.closeDialog();refresh()})}
  function modelEdit(mode,index){const c=mode==='edit'?state.connections[index]:{name:'',mode:mode==='private'?'企业私有模型':'外部 API',endpoint:'',model:'',scope:'仅当前企业'};modal(mode==='edit'?'编辑模型连接':'新增模型连接',`${field('连接名称','name',c.name,'text','required')}${select('部署方式','mode',[['企业私有模型','企业私有模型'],['外部 API','外部 API']],c.mode)}${field('服务地址','endpoint',c.endpoint,'url','required')}${field('模型标识','model',c.model,'text','required')}${select('资料权限','scope',[['仅当前企业','仅当前企业'],['指定项目','指定项目']],'仅当前企业')}`,'保存配置',data=>{const next={...Object.fromEntries(data),enabled:mode==='edit'?c.enabled:false};if(mode==='edit')state.connections[index]=next;else state.connections.push(next);record('保存模型连接',data.get('name'));ui.closeDialog();refresh()})}
  function enterprise(index){const names=['账号与单点登录','资料系统连接','专家服务协作'],c=state[`enterprise:${index}`]||{name:names[index],owner:project().owner,scope:'指定项目',configuration:['企业身份登录；项目角色继承企业岗位授权；会话有效期 8 小时。','同步项目资料与法规目录；更新生成新版本；保留原引用版本。','注册专家负责审查与意见回复；授权限定到当前项目；交付后终止访问。'][index]};modal(names[index],`${field('名称','name',c.name,'text','required')}${field('负责人','owner',c.owner,'text','required')}${select('授权范围','scope',[['当前企业','当前企业'],['指定项目','指定项目']],c.scope)}<label class="field">连接与授权配置<textarea name="configuration" required>${e(c.configuration)}</textarea></label>`,'保存连接',data=>{state[`enterprise:${index}`]=Object.fromEntries(data);record('配置企业连接',names[index]);ui.closeDialog()})}
  return {enhance,handle:(action,value)=>{
    if(action==='final-preview'){modal(value,value==='正文'?task().modules.map(m=>`<h3>${e(m.name)}</h3><p class="final-text">${e(m.text)}</p>`).join(''):value==='检查报告'?table(['问题','结果'],getIssues().map(i=>`<tr><td>${e(i.name)}</td><td>${e(i.status)}</td></tr>`)):value==='来源附录'?table(['资料','版本','位置','操作'],sourceRows([...new Set(task().modules.flatMap(m=>(m.refs||[]).map(r=>r.id)))])):task().comments.map(c=>`<blockquote>${e(c.quote)}</blockquote><p>${e(c.text)}</p>`).join(''));return true}
    return handle(action,value);
  }};
}
