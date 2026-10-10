(() => {
  'use strict';
  const managed = new Set(['S01','S04','S05','S06','S07','S09','S11','S13','S15','S18']);
  const baseShowScreen = showScreen;
  const {escape,button,tag,options,title,empty,table}=WorkspaceUI;
  const gruAvatar = (placement, description='') => `<img class="gru-avatar gru-avatar-${placement}" src="ui-delivery/assets/master/gru-avatar-human.png" alt="${escape(description)}" width="96" height="96">`;
  const clockText = () => new Date().toLocaleString('zh-CN',{hour12:false});
  const projects = demoProjects.map((row,index)=>({id:`project-${index+1}`,row,name:row[0],product:row[1],category:row[2],path:row[3],status:row[4],owner:'林知远'}));
  let project = projects[0];
  const categories = {project:'项目资料',regulations:'法规与标准',templates:'模板',rules:'检查规则'};
  const materials = Object.entries(knowledgeDemoRows).flatMap(([category,rows])=>rows.map(row=>({category,name:row[0].replace('（演示）','').replace('（演示版）',''),source:row[2],version:row[3],scope:category==='project'?'project':'company',projectId:category==='project'?projects[0].id:null,status:row[5],updated:row[6],author:category==='project'?'周予安':'沈知微',versions:[],parsed:true,uploaded:false})));
  materials.forEach((item,index)=>{
    item.demo=true;
    item.id=`material-${index}`;
    item.location=['第 3 页 · 产品信息','第 2 页 · 结构与组成','第 1 页 · 职责分工','指标表 · 型号字段'][index] || (item.category==='templates'?'模板目录与编写要求':'适用范围与核验要求');
    item.text=index===0?`产品名称：${projects[0].product}\n器械类别：${projects[0].category}\n注册路径：${projects[0].path}`:index===1?'文件包含主机、采集模块与配套附件的组成说明。引用前需要由研发人员核准本版本。':index===2?'注册审查：林知远\n研发支持：周予安\n知识核准：沈知微':index===3?'型号：MX-Demo-02\n核验要求：名称、型号、单位和适用条件须逐项核对。':item.category==='templates'?'目录要求：产品概述、结构与组成、性能指标、检验方法。各章节记录资料版本与原文位置。':item.category==='rules'?'检查要求：记录来源名称与版本，核对原文位置；缺少证据或字段冲突时交由人员处理。':'来源登记应包含原文地址、发布信息、实施状态和适用范围。本记录等待授权人员核准。';
    item.versions.push({version:item.version,text:item.text,status:item.status,location:item.location,updated:item.updated});
  });
  const material = id => {const value=materials.find(item=>item.id===id);if(!value)throw new Error(`资料不存在：${id}`);return value};
  for(const [id,catalog] of [['material-7','监管信息、综述资料、非临床资料、临床评价资料、说明书与标签、质量管理体系'],['material-8','产品名称、型号规格及划分、性能指标、检验方法、术语（如适用）、附录（如适用）']]){const template=material(id);template.catalog=catalog;template.text=`设计演示目录：${catalog}。适用性及具体条款需由注册人员结合项目资料确认。每项记录资料版本和原文位置。`;template.versions[0].text=template.text}
  const names = {requirements:'产品技术要求',index:'注册文档目录',overview:'综述资料',ifu:'说明书',label:'标签样稿',principles:'基本原则清单',risk:'风险管理',research:'研究报告',clinical:'临床评价',qms:'质量体系',change:'变更资料',renewal:'延续资料',review:'注册资料总稿'};
  const chapterNames = type => {
    if(type==='index')return ['监管信息','综述资料','非临床资料','临床评价资料','说明书与标签','质量管理体系'];
    if(type==='requirements')return ['产品名称','型号规格及划分','性能指标','检验方法','术语（如适用）','附录（如适用）'];
    if(type==='review')return ['产品概述','结构与组成','性能指标','检验方法'];
    if(type==='change')return ['变更事项','变更影响','验证证据','相关文件'];
    const definition=fields.definitions[type];if(!definition)throw new Error('文档类型不存在');return definition[1].map(row=>row[0]);
  };
  const sourceIds=['material-0','material-1','material-3','material-9'];
  const versions=[{id:'D-1.4',name:'注册文档 Agent',status:'已发布',model:'演示连接',budget:12,template:'material-8',rule:'material-10',reviewer:'林知远',categories:['第二类','第三类'],checked:true,note:'当前演示任务使用的配置',published:'2026-09-24'},{id:'D-1.5',name:'注册文档 Agent',status:'草稿',model:'演示连接',budget:12,template:'material-8',rule:'material-10',reviewer:'林知远',categories:['第二类','第三类'],checked:false,note:'默认计划与来源检查配置',published:''}];
  const tasks=[];
  const events=[];
  const log = (action,taskId='',detail='') => events.unshift({time:clockText(),date:new Date().toLocaleDateString('sv-SE'),action,taskId,detail,person:'林知远'});
  function createTask(p,type,status='待人工审查',owner='林知远',blank=false) {
    const task={id:`task-${tasks.length+1}`,projectId:p.id,type,name:names[type],owner,status,revision:1,savedRevision:1,reviewRevision:null,signedRevision:null,dirty:false,verified:{},comments:[],history:[],agent:'D-1.4',due:'2026-09-26',waiting:'2 天',run:status==='运行中'?'运行中':'草稿已准备',cost:null,modules:chapterNames(type).map((name,index)=>{
      if(blank)return {id:`chapter-${index+1}`,name,sourceId:null,sourceVersion:'',sourceText:'',location:'',text:'',refs:[]};
      const source=material(sourceIds[Math.min(index,sourceIds.length-1)]);
      return {id:`chapter-${index+1}`,name,sourceId:source.id,sourceVersion:source.version,sourceText:source.text,location:source.location,text:type==='index'?`${name}：本项目为${p.product}，注册路径为${p.path}。本节登记对应资料、适用版本和负责人。`:[`产品名称：${p.product}。名称需与申请表和说明书保持一致，资料用于${p.path}的准备。`,'分别登记型号规格及划分依据，可使用结构图与比较表。含有软件时，登记发布版本及完整版本命名规则。','逐项登记可客观判定的成品功能、安全及质量控制指标，标明单位和适用条件。研究性与评价性内容由独立研究报告保存。','检验方法编号与性能指标编号对应，记录试验条件、操作步骤、结果判定及引用标准的编号和年代号。','登记需要解释的专用术语及定义；不适用时记录人工确认依据。','按产品情况记录附录内容与适用依据。正式条款、指标及方法仍需产品资料和注册人员确认。'][index]};
    })};
    task.history.push({revision:1,time:'2026-09-24',action:'任务版本已准备',texts:task.modules.map(x=>x.text)});
    tasks.push(task);return task;
  }
  createTask(projects[0],'requirements');createTask(projects[0],'index','运行中','周予安');createTask(projects[0],'change','待补资料','沈知微');createTask(projects[0],'review','已退回','顾明川');
  let task=tasks[0],screen='S01',chapter=0,selectedMaterial=materials[0].id,sourceReturn=null;
  let queueTab='mine',queueFilter='',library={scope:'project',category:'all',status:'',query:'',page:1,size:10,tab:'content'},taskFilter='';
  let configId='D-1.4',configTab='defaults',agentArea='agents',evalTab='overview',evalFilters={category:'',from:'2026-09-01',to:'2026-09-30'},compareA='D-1.4',compareB='D-1.5';
  const grades={},imports=[];
  const folders=Object.entries(categories).map(([id,name])=>({id,name,parent:null,category:id}));
  const libraryState={view:'all',folder:'',mode:'list',opened:[],selected:new Set(),sort:'updated',tag:'',owner:'',recent:[]};
  materials.forEach(item=>Object.assign(item,{folderId:item.category,tags:[],favorite:false,deleted:false,activity:[]}));
  const conversations=[];
  const assistant={open:false,expanded:false,history:false,current:null,draft:'',mode:'question',sequence:0};
  let role='reviewer',newType='requirements',newNote='',dialogSubmit=null,lastFocus=null,selectedRange=null,selectedQuote='',selectedModule=0;
  let taskParent='S18',setupTaskId=null,navigationReady=false,restoringNavigation=false;
  const dialog=document.createElement('dialog');dialog.className='product-dialog';dialog.id='product-dialog';dialog.setAttribute('aria-labelledby','product-dialog-title');document.body.append(dialog);
  let selectedFigure=null;
  const selection=document.createElement('div');selection.className='selection-popover';selection.hidden=true;selection.setAttribute('role','toolbar');selection.setAttribute('aria-label','选中内容操作');selection.innerHTML='<button data-action="replace-selection">修改文字</button><button data-action="comment-selection">添加标注</button><button data-action="comment-image" hidden>添加标注</button><button data-action="ask-selection">询问 Gru</button>';document.body.append(selection);
  const assistantPanel=document.createElement('aside');assistantPanel.id='conversation-agent';assistantPanel.className='conversation-agent';assistantPanel.hidden=true;assistantPanel.setAttribute('aria-label','Gru 对话助手');document.body.append(assistantPanel);
  const assistantLauncher=document.createElement('button');assistantLauncher.id='agent-launcher';assistantLauncher.className='agent-launcher';assistantLauncher.dataset.action='assistant-toggle';assistantLauncher.setAttribute('aria-controls','conversation-agent');assistantLauncher.setAttribute('aria-expanded','false');assistantLauncher.setAttribute('aria-label','打开 Gru 对话助手');assistantLauncher.innerHTML=gruAvatar('launcher')+'<span class="agent-launcher-label">Gru</span><span class="agent-launcher-hint">提问、查资料、处理当前工作</span><kbd>⌘ J</kbd>';document.querySelector('.topbar').insertBefore(assistantLauncher,document.querySelector('.topbar').lastElementChild);
  function openDialog(name,body,confirm='确认',submit=null) {
    lastFocus=document.activeElement;dialogSubmit=submit;
    dialog.innerHTML=`<form id="product-dialog-form"><header class="dialog-head"><h2 id="product-dialog-title">${escape(name)}</h2><button type="button" class="btn small" data-action="dialog-close" aria-label="关闭弹窗">关闭</button></header><div class="dialog-body">${body}<div id="product-dialog-error" role="alert" hidden></div></div><footer class="dialog-foot">${button('返回','dialog-close')}<button type="submit" class="btn primary">${confirm}</button></footer></form>`;
    dialog.showModal();
  }
  function dialogError(message){const node=document.getElementById('product-dialog-error');node.hidden=false;node.className='dialog-error';node.textContent=message;node.scrollIntoView({block:'nearest'})}
  function closeProductDialog(){dialog.close();dialogSubmit=null;if(lastFocus?.isConnected)lastFocus.focus()}
  dialog.addEventListener('submit',event=>{event.preventDefault();if(dialogSubmit)dialogSubmit(new FormData(event.target));else closeProductDialog()});
  function setScreen(id){showScreen(id)}
  const modelManager=createModelApiManager({escape,button,tag,table,empty,options,clockText,projects,versions,openDialog,closeProductDialog,dialogError,log,showFeedback,refresh:()=>renderScreen('S13'),open:()=>{agentArea='models';setScreen('S13')},openAgent:id=>{configId=id;configTab='model';agentArea='agents';setScreen('S13')}});
  const knowledge=createKnowledgeWorkspace({materials,projects,tasks,openDialog,closeProductDialog,dialogError,clockText,log,project:()=>project,notify:showFeedback,refresh:()=>renderScreen('S04'),refreshCurrent:()=>renderScreen(screen),pending:()=>{libraryState.view='pending';libraryState.mode='list';libraryState.folder='';library.scope='all';library.page=1;renderScreen('S04')},openLibrary:()=>setScreen('S04'),openMaterial:id=>{selectedMaterial=id;library.tab='content';setScreen('S05')},openTask:id=>useTask(id),openVersion:(id,version)=>{closeProductDialog();const record=material(id).versions.find(v=>v.version===version);if(!record)throw new Error('资料版本不存在');selectedMaterial=id;sourceReturn={materialId:id,version,text:record.text,location:record.location,label:'固定资料版本',screen:'S04'};library.tab='content';setScreen('S05')},download:downloadFile});
  const capabilities=createAgentCapabilities({openDialog,closeProductDialog,dialogError,refresh:()=>renderScreen('S13'),models:modelManager,collections:knowledge.collections,changed:(config,action)=>{config.checked=false;Object.keys(grades).filter(key=>key.startsWith(`${config.id}:`)).forEach(key=>delete grades[key]);log(action,'',config.id)}});
  const complexTasks=installComplexDemo({materials,tasks,projects,createTask,knowledge});
  const research=createResearchWorkspace({materials,projects,tasks,knowledge,defaultCollection:()=>versions.findLast(v=>v.status==='已发布'&&v.name==='注册文档 Agent')?.capabilities?.defaultCollection,openDialog,closeProductDialog,dialogError,clockText,refresh:()=>renderScreen(screen),notify:showFeedback,conversation:activeConversation,contextSources,renderAssistant,locate:(id,index)=>{if(index<0)throw new Error('问题章节不存在');useTask(id,screen,index);document.getElementById(`task-section-${index}`).scrollIntoView({block:'center'})},ask:(target,index,question)=>{const chapterIndex=target.modules.findIndex(m=>m.name===question.chapter);useTask(target.id,screen,chapterIndex);const context=pageAgentContext();context.questionIndex=index;context.chapter=chapterIndex;context.label=`${target.name} · ${question.question}`;context.materialIds=question.sources.map(value=>value.split(':')[0]);context.key=`question:${target.id}:${index}:${target.revision}`;showAssistant(context);assistant.draft=`请协助核查：${question.question}。完成条件：${question.condition}`;renderAssistant()},openLibrary:()=>setScreen('S04'),openMaterial:id=>{selectedMaterial=id;library.tab='content';setScreen('S05')}});
  const dossier=createRegistrationDossier({materials,tasks,projects,openDialog,closeProductDialog,dialogError,refresh:()=>renderScreen(screen),notify:showFeedback,event:(target,kind,text)=>research.recordEvent(target,kind,text),invalidate:(target,reason)=>{task=target;project=projects.find(p=>p.id===target.projectId);invalidation(reason)},openOriginal:(target,index,ref)=>{selectedMaterial=ref.id;sourceReturn={taskId:target.id,chapter:index,materialId:ref.id,version:ref.version,text:ref.text,location:ref.location,page:ref.page,screen};library.tab='content';setScreen('S05')}},knowledge);
  const fields=createDocumentFields({materials,tasks,projects,openDialog,closeProductDialog,dialogError,refresh:()=>renderScreen(screen),notify:showFeedback,download:downloadFile,openTask:id=>useTask(id,'S09'),installPublic:()=>installPublicReference({materials,projects,createTask,fields})});
  const finalProduct=createFinalProduct({materials,tasks,projects,versions,events,names,chapterNames,context:()=>({project,task,newType,screen,setupTaskId}),openDialog,error:dialogError,closeDialog:closeProductDialog,record:log,refresh:()=>renderScreen(screen),navigate:id=>execute('navigate',id),read:index=>{chapter=index;setScreen('S09');document.getElementById(`task-section-${index}`).scrollIntoView({block:'center'})},selectedSources:()=>research.selected(project.id),download:downloadFile,documentHtml:dossier.documentHtml,create:(type,mapping)=>{
    const sources=research.selected(project.id);task=createTask(project,type,'待人工审查',project.owner,true);task.agent='D-1.4';task.inputScope=research.scope(project.id);task.due='未设置';task.waiting='刚创建';task.run='章节已准备';setupTaskId=task.id;chapter=0;
    task.modules.forEach((m,index)=>{const assigned=mapping.rows[index].sources.map(id=>material(id));if(!assigned.length)return;const source=assigned[0];Object.assign(m,{text:assigned.map(item=>item.text).join('\n\n'),sourceId:source.id,sourceVersion:source.version,sourceText:source.text,location:source.location,refs:assigned.map(item=>({...knowledge.snap(item,item.version),type:'text',page:null,caption:'',checked:false}))})});
    task.history[0].texts=task.modules.map(m=>m.text);history.replaceState(navigationState(),'',location.href);setScreen('S09');
  },configPage:renderAgents});
  function useTask(id,destination='S09',index=0){const found=tasks.find(x=>x.id===id);if(!found)throw new Error('任务不存在');if(!['S07','S09','S11','S05'].includes(screen))taskParent=screen==='S06'?'S06':'S18';task=found;project=projects.find(x=>x.id===task.projectId);setupTaskId=task.id;chapter=index;setScreen(destination)}
  function projectLocation(id){
    if(!['S18','S06','S07','S09','S11'].includes(id))return '';
    const parts=[button('项目中心','navigate','S03'),id==='S18'?`<span aria-current="page">${escape(project.name)}</span>`:button(project.name,'project-open',project.id)];
    if(id==='S06')parts.push('<span aria-current="page">文档任务</span>');
    if(['S07','S09','S11'].includes(id)){parts.push(button('文档任务','navigate','S06'));parts.push(`<span aria-current="page">${id==='S07'&&!setupTaskId?'创建任务':escape(task.name)}</span>`)}
    return `<nav class="project-location" aria-label="项目层级">${parts.join('<span aria-hidden="true">/</span>')}</nav>`;
  }
  function taskBack(){return button(taskParent==='S06'?'返回任务列表':'返回项目详情','navigate',taskParent)}
  function taskContext(){return `<div class="task-context"><div><button class="quiet-link" data-action="project-open" data-value="${project.id}">${escape(project.name)}</button><span>/</span><strong>${escape(task.name)}</strong>${tag(task.status,task.status==='已签发'?'ok':'info')}</div><div><span>文档 v${task.revision}</span><span>负责人 ${escape(task.owner)}</span>${button('版本记录','document-history')}</div></div>`}
  function flow(current){const existing=current!=='S07'||Boolean(setupTaskId),steps=[['S07',existing?'任务配置':'创建任务'],['S09','撰写与核验'],['S11','审查与签发']],position=steps.findIndex(([id])=>id===current);return `<nav class="flow-strip workflow-progress" aria-label="注册文档流程"><ol>${steps.map(([id,label],index)=>`<li class="${index===position?'current':index<position?'visited':'upcoming'}"><button data-action="task-step" data-value="${id}" ${!existing&&id!=='S07'?'disabled':''} ${id===current?'aria-current="step"':''}><span class="workflow-number" aria-hidden="true">${index+1}</span><span>${label}</span></button></li>`).join('')}</ol></nav>`}
  function invalidation(reason){
    if(!task.dirty){task.revision+=1;task.dirty=true;log('正文修改',task.id,reason)}
    task.reviewRevision=null;task.signedRevision=null;task.verified={};task.status='待人工审查';
    task.modules.forEach(m=>m.refs?.forEach(r=>r.checked=false));
  }
  function blockers(){
    const list=[];
    if(task.publicReference)list.push('公开参考任务未核准中国项目适用性，不能作为申报文稿签发');
    const citations=dossier.problems(task);if(citations.length)list.push(`${citations.length} 项图文引用等待核查`);
    if(task.dirty)list.push('正文修改尚未保存');
    const unverified=task.modules.filter((m,index)=>task.verified[index]!==task.revision).length;
    if(unverified)list.push(`${unverified} 个章节需要来源核验`);
    const missing=task.modules.filter(m=>!m.sourceId||!m.text.trim()).length;if(missing)list.push(`${missing} 个章节等待正文或来源`);
    if(task.templatePending)list.push('文档模板等待核准');
    const changed=task.modules.filter(m=>m.sourceId&&material(m.sourceId).version!==m.sourceVersion).length;
    if(changed)list.push(`${changed} 项资料出现新版本，请检查影响`);
    const unavailable=task.modules.filter(m=>m.sourceId&&material(m.sourceId).status!=='approved').length;
    if(unavailable)list.push(`${unavailable} 项引用资料尚未核准`);
    const comments=task.comments.filter(c=>!c.resolved).length;if(comments)list.push(`${comments} 条批注等待处理`);
    const researchProblems=task.research?.questions.filter(q=>q.blocking&&q.status!=='已核验').length;if(researchProblems)list.push(`${researchProblems} 项研究问题等待核验`);
    if(task.status==='已退回'||task.status==='待补资料')list.push(`任务${task.status}，需要负责人提交修订`);
    return list;
  }
  function saveTask(){
    if(task.dirty){task.savedRevision=task.revision;task.history.unshift({revision:task.revision,time:clockText(),action:'正文已保存',texts:task.modules.map(x=>x.text),modules:structuredClone(task.modules)});task.dirty=false;log('保存正文',task.id,`v${task.revision}`)}
    renderScreen(screen);showFeedback('当前会话中的文档版本已保存。');
  }
  function taskRows(items){return items.map(t=>`<tr data-task-row="${t.id}"><td><strong>${escape(t.name)}</strong><small>文档 v${t.revision} · ${escape(t.agent)}</small></td><td>${tag(t.status,t.status==='已签发'?'ok':t.status==='待补资料'||t.status==='已退回'?'warn':'info')}</td><td>${escape(t.owner)}</td><td>${escape(t.due)}</td><td>${t.comments.filter(c=>!c.resolved).length} 条批注</td><td>${button('打开任务','task-open',t.id)}</td></tr>`)}
  function renderWorkbench(){
    const pending=tasks.filter(t=>t.status!=='已签发');
    const rows=pending.filter(t=>queueTab==='mine'?t.owner==='林知远':queueTab==='waiting'?t.owner!=='林知远':true).filter(t=>!queueFilter||t.status===queueFilter);
    return title('工作台',button('项目中心','navigate','S03'))+`<div class="work-intro"><div><h2>今天，先处理需要你决定的事项</h2><p>当前身份：林知远 · 注册审查员 · 2026 年 9 月 26 日</p></div></div><div class="work-metrics">${[['待我处理',pending.filter(t=>t.owner==='林知远').length,'我的审查与确认','mine'],['等待他人',pending.filter(t=>t.owner!=='林知远').length,'查看负责人和阻塞事项','waiting'],['运行任务',tasks.filter(t=>t.status==='运行中').length,'查看当前任务进度','running'],['待签发',tasks.filter(t=>t.status==='待签发').length,'查看已确认文档','signing']].map(([label,count,detail,key])=>`<button class="work-metric" data-action="queue-filter" data-value="${key}"><span>${label}</span><strong>${count}</strong><small>${detail}</small></button>`).join('')}</div><div class="work-columns"><section class="card pad"><div class="panel-top"><h2>待处理事项</h2><span class="muted small">${rows.length} 项</span></div><div class="queue-tabs" role="tablist">${[['mine','由我处理'],['waiting','等待他人'],['all','全部事项']].map(([key,label])=>`<button role="tab" aria-selected="${queueTab===key}" data-action="queue-tab" data-value="${key}">${label}</button>`).join('')}</div>${rows.map(t=>`<article class="queue-row"><div><div class="tag-row">${tag(t.status,t.status==='已退回'?'error':'warn')}${tag(`截止 ${t.due}`)}</div><strong>${escape(t.name)}</strong><p>${escape(projects.find(p=>p.id===t.projectId).name)}</p><p class="queue-reason">${t.status==='运行中'?'章节内容正在准备':t.status==='已退回'?'需要负责人修改正文并提交复核':t.status==='待补资料'?'来源资料未核准，等待补充证据':'需要核对来源并确认当前版本'}</p><p>负责人 ${escape(t.owner)} · 已等待 ${t.waiting}</p></div>${button(t.owner==='林知远'?'开始处理':'查看进度','task-open',t.id,t.owner==='林知远')}</article>`).join('')||empty('当前没有待处理事项','切换其他范围查看任务。')}</section><aside class="work-side"><section class="card pad"><div class="panel-top"><h2>场景 Agent</h2>${button('管理','navigate','S13')}</div><div class="agent-launch"><div class="tag-row">${tag('已发布','ok')}<span class="muted small">D-1.4</span></div><h3>注册文档 Agent</h3><p>选择项目与目标文档，系统准备章节、资料和审查要求。</p>${button('创建文档任务','navigate','S07',true)}</div>${['法规版本比较','资料完整性检查','产品技术问答','售后资料助手','销售资料助手'].map(name=>`<div class="agent-mini"><span>${name} Agent</span><button class="quiet-link" data-action="planned-agent" data-value="${name}">查看范围</button></div>`).join('')}</section><section class="card pad"><div class="panel-top"><h2>最近项目</h2></div>${projects.slice(0,3).map(p=>`<div class="project-mini"><strong>${escape(p.name)}</strong><p>${escape(p.category)} · ${escape(p.path)}</p><button class="quiet-link" data-action="project-open" data-value="${p.id}">查看项目与任务</button></div>`).join('')}</section></aside></div>`;
  }
  function renderProject(){
    const own=tasks.filter(t=>t.projectId===project.id),waiting=own.filter(t=>['待补资料','已退回','待人工审查'].includes(t.status));
    const available=materials.filter(m=>m.scope==='company'||m.projectId===project.id);
    return title('项目详情',button('返回项目中心','navigate','S03')+button('创建文档任务','navigate','S07',true))+`<section class="project-summary"><div class="tag-row">${tag(project.category,'info')}${tag(demoStatus[project.status][0])}</div><h2>${escape(project.name)}</h2><p>${escape(project.product)} · ${escape(project.path)} · 负责人 ${escape(project.owner)}</p></section><div class="project-grid"><div><div class="project-blocker"><strong>${waiting.length?`${waiting.length} 项任务需要人员处理`:own.length?'当前没有阻塞任务':'项目尚未创建文档任务'}</strong><p>${waiting.length?`${waiting[0].name} · 负责人 ${waiting[0].owner} · ${waiting[0].status}`:'查看资料准备情况后选择适用文档。'}</p>${button(waiting.length?'处理首项任务':'创建文档任务',waiting.length?'task-open':'navigate',waiting.length?waiting[0].id:'S07',true)}</div><section class="card pad"><div class="panel-top"><h2>项目办理进度</h2><span class="muted small">以任务与资料状态判断</span></div><div class="journey">${[['项目建档','已登记','project-info'],['资料准备',`${available.filter(m=>m.status==='approved').length} 项已核准`,'project-materials'],['任务计划',`${own.length} 项任务`,'project-tasks'],['撰写与核验',`${own.filter(t=>t.status==='运行中').length} 项运行中`,'project-tasks'],['审查与签发',`${own.filter(t=>t.status==='已签发').length} 项已签发`,'project-tasks']].map(([name,detail,action],index)=>`<button class="${index===(waiting.length?4:1)?'current':''}" data-action="${action}"><strong>${name}</strong>${detail}</button>`).join('')}</div></section><section class="card pad" style="margin-top:18px"><div class="panel-top"><h2>文档任务</h2>${button('全部任务','navigate','S06')}</div>${own.length?table(['文档与版本','状态','负责人','截止时间','处理意见','操作'],taskRows(own)):empty('当前项目没有任务','创建任务后将在此显示独立的正文、来源和审查记录。')}</section></div><aside><section class="card pad"><h2>资料准备</h2><div class="facts-grid"><div><span>项目专属资料</span><strong>${available.filter(m=>m.scope==='project').length} 项</strong></div><div><span>公共模板与规则</span><strong>${available.filter(m=>m.scope==='company').length} 项</strong></div><div><span>待核准资料</span><strong>${available.filter(m=>m.status==='pending').length} 项</strong></div></div>${button('查看项目资料','project-materials')}</section><section class="card pad" style="margin-top:18px"><h2>协作人员</h2><div class="facts-grid"><div><span>注册审查</span><strong>林知远</strong></div><div><span>研发支持</span><strong>周予安</strong></div><div><span>知识核准</span><strong>沈知微</strong></div></div></section></aside></div>`;
  }
  function renderTasks(){
    const stages={writing:['待补资料','运行中','已退回'],review:['待人工审查','待签发','已签发']};
    const own=tasks.filter(t=>t.projectId===project.id&&(!taskFilter||(stages[taskFilter]?stages[taskFilter].includes(t.status):t.status===taskFilter)));
    return title('文档任务',button('返回项目详情','navigate','S18')+button('创建文档任务','navigate','S07',true))+`<section class="card pad"><div class="toolbar"><label>任务状态<select data-change="task-filter">${options([['','全部状态'],['writing','撰写与核验'],['review','审查与签发'],...['待补资料','运行中','待人工审查','待签发','已退回','已签发'].map(x=>[x,x])],taskFilter)}</select></label><span class="muted small">${own.length} 项任务</span></div>${own.length?table(['文档与版本','状态','负责人','截止时间','批注','操作'],taskRows(own)):empty('没有符合条件的任务','调整状态筛选，或创建文档任务。')}</section>`;
  }
  function requirementsForSetup(){const config=versions.findLast(x=>x.status==='已发布'&&x.name==='注册文档 Agent');const templateId=newType==='index'?'material-7':newType==='requirements'?config?.template:materials.find(m=>m.category==='templates'&&m.documentType===newType)?.id;return {config,template:templateId?material(templateId):null,docs:materials.filter(m=>m.projectId===project.id&&m.category==='project'&&!m.deleted&&m.status!=='disabled')}}
  function renderSetup(){
    if(setupTaskId)return title('任务配置',taskBack()+button('继续撰写','navigate','S09',true))+flow('S07')+`<div class="setup-grid"><section class="card pad"><h2>${escape(task.name)}</h2><div class="facts-grid">${[['项目',project.name],['文档类型',names[task.type]],['负责人',task.owner],['Agent 版本',task.agent],['文稿版本',`v${task.revision}`],['模板',task.template?`${material(task.template.id).name} ${task.template.version}`:'待核准']].map(([label,value])=>`<div><span>${label}</span><strong>${escape(value)}</strong></div>`).join('')}</div>${button('调整资料范围','research-task-scope',task.id,false,task.signedRevision===task.revision)}</section><section class="card pad"><h2>章节目录</h2>${(task.modules.length?task.modules.map(m=>m.name):chapterNames(task.type)).map((name,index)=>`<div class="plan-row"><span>${index+1}</span><strong>${escape(name)}</strong></div>`).join('')}</section></div>`;
    const {config,template,docs}=requirementsForSetup();
    return title('创建文档任务',button('返回项目','navigate','S18'))+flow('S07')+`<div class="setup-grid"><section class="card pad"><h2>确认项目与目标文档</h2><div class="form"><label class="field">项目<select id="current-project" data-change="setup-project">${options(projects.map(p=>[p.id,p.name]),project.id)}</select></label><label class="field">目标文档<select id="document-type" data-change="setup-type">${options([['requirements','产品技术要求'],['index','注册文档目录']],newType)}</select></label><label class="field">补充说明（选填）<textarea id="task-note" data-change="task-note" placeholder="需要重点关注的资料或内容">${escape(newNote)}</textarea></label></div><div class="alert info">${escape(project.category)} · ${escape(project.path)}。默认计划依据目标文档和已发布 Agent 配置。</div><details style="margin:18px 0"><summary>查看默认配置与适用依据</summary><div class="facts-grid"><div><span>Agent 配置</span><strong>${config?escape(config.id):'尚无已发布版本'}</strong></div><div><span>模板</span><strong>${template?escape(template.name)+' '+template.version:'未配置'}</strong></div><div><span>审查责任人</span><strong>${config?escape(config.reviewer):'未配置'}</strong></div><div><span>任务预算</span><strong>${config?config.budget:0} 演示单位 / 任务</strong></div></div>${button('查看平台默认配置','config-current')}</details><h3>创建前检查</h3>${[[config?'已发布版本可用':'缺少已发布版本',Boolean(config),''],[template?`${template.name} ${template.version}`:'缺少适用模板',template?.status==='approved',template?.id||''],[`当前项目资料 ${docs.length} 项`,docs.length>0,'']].map(([label,ready,id])=>`<div class="requirement-row"><div>${escape(label)}</div>${ready?tag('满足','ok'):id?button('核准资料','material-open',id):tag('需要补充','warn')}</div>`).join('')}<div style="margin-top:20px">${button('创建任务并打开正文','create-task','',true)}</div><div id="setup-error" role="alert"></div></section><section class="card pad"><div class="panel-top"><h2>系统准备的章节计划</h2>${tag('随目标文档更新','info')}</div>${chapterNames(newType).map((name,index)=>`<div class="plan-row"><span>${String(index+1).padStart(2,'0')}</span><div><strong>${name}</strong><p>${newType==='index'?'登记资料、适用版本、负责人和核准状态':'从项目资料读取对应内容，引用来源并进行人工核验'}</p></div></div>`).join('')}<div class="source-excerpt">资料不足时任务进入待补资料。系统显示缺少内容和负责人，保留当前项目与文档类型。</div></section></div>`;
  }
  // 页面内容与动作在同一任务状态上读取和更新。
  const renderers={S01:renderWorkbench,S18:renderProject,S06:renderTasks,S07:renderSetup};
  function renderScreen(id){
    if(!managed.has(id))return;
    clearSelection();
    if(id==='S09'||id==='S11')dossier.prepare(task);
    const node=document.getElementById(id);node.classList.add('product-page');node.innerHTML=renderers[id]();
    if(id==='S07'){
      const typeSelect=node.querySelector('#document-type');if(typeSelect)typeSelect.innerHTML=options(Object.entries(names),newType);
      if(!setupTaskId)node.querySelector('.setup-grid>.card .form').insertAdjacentHTML('afterend',research.setup(project.id));
      else node.querySelector('.setup-grid>.card').insertAdjacentHTML('beforeend',button('选择文档模板','task-template',task.id,false,task.signedRevision===task.revision));
    }
    node.insertAdjacentHTML('afterbegin',projectLocation(id));
    if(id==='S18'){
      const heading=node.querySelector('.heading'),toolbar=document.createElement('div');toolbar.className='project-toolbar';
      toolbar.append(node.querySelector('.project-location'),heading.querySelector('.actions'));heading.replaceWith(toolbar);
      const name=node.querySelector('.project-summary h2'),projectHeading=document.createElement('h1');projectHeading.textContent=project.name;name.replaceWith(projectHeading);
    }
    if(['S09','S11'].includes(id))node.querySelector('.heading .actions').insertAdjacentHTML('afterbegin',taskBack());
    if(id==='S09'&&node.querySelector('.reader-research'))node.querySelector('.reader-research').innerHTML=`<div id="review-readiness" class="review-readiness">${blockers().map(escape).join('<br>')||'当前版本可以进入审查'}</div>${research.taskPanel(task)}${['已退回','待补资料'].includes(task.status)?button('提交修订并重新检查','resume-task'):''}${button('导出当前文稿','export-document')}`;
    if(id==='S09'&&node.querySelector('.writing-layout')){
      node.querySelectorAll('.document-module').forEach((section,index)=>{const key=`${task.id}:${index}`;section.insertAdjacentHTML('beforeend',`<details class="evidence-fold" data-reader-fold="${key}" ${DocumentReader.state.folds.has(key)?'open':''}><summary>引用证据 <span>${task.modules[index].refs.length} 项 · ${task.verified[index]===task.revision?'已核验':'待核验'}</span></summary>${dossier.citations(task,index)}${button('核验本章来源','chapter-source',index)}</details>`)});
      node.querySelector('.document-outline').insertAdjacentHTML('afterend',`<section class="reader-dossier">${dossier.banner(project.id)}</section>${dossier.inspect(task)}`);
    }
    if(id==='S18')node.querySelector('.project-grid').insertAdjacentHTML('beforebegin',dossier.banner(project.id));
    if(id==='S18'&&project.publicReference){node.querySelector('.dossier-banner small').textContent='公开参考项目；以下为中国首次注册要求的参考目录，尚未核准本项目适用性。';node.querySelectorAll('.project-grid>aside>section:nth-child(2) strong').forEach(n=>n.textContent='待指定')}
    if(task.publicReference&&id==='S09'&&node.querySelector('.dossier-banner'))node.querySelector('.dossier-banner small').textContent='公开参考目录，项目适用性待核准。';
    node.querySelectorAll('.reader-operations,.review-decision').forEach(control=>{control.dataset.readerFold=`${task.id}:${control.classList.contains('reader-operations')?'operations':'decision'}`;control.open=DocumentReader.state.folds.has(control.dataset.readerFold)});
    if(id==='S04')node.querySelector('.heading').insertAdjacentHTML('afterend',knowledge.progress());
    if((id==='S09'||id==='S11')&&!task.modules.length)node.querySelector('.empty-message').insertAdjacentHTML('beforeend',button('选择补充资料','research-task-scope',task.id));
    if(id==='S18')node.querySelector('.project-grid>aside').insertAdjacentHTML('beforeend',`<section class="card pad knowledge-usage"><h2>项目资料集</h2><p>按版本选择本项目的任务依据。</p>${button('管理资料集','project-collections')}</section>`);
    if((id==='S04'||id==='S05')&&node.querySelector('.library-reader')){
      const reader=node.querySelector('.library-reader'),item=material(selectedMaterial);
      reader.querySelector('.panel-top').insertAdjacentHTML('afterend',knowledge.statusStrip(item));
      reader.insertAdjacentHTML('beforeend',knowledge.nextStep(item));
      if(library.tab==='content')reader.insertAdjacentHTML('beforeend',dossier.materialPages(sourceReturn?.materialId===item.id?item.versions.find(v=>v.version===sourceReturn.version):item));
      if(item.publicOriginal){reader.querySelector('.panel-top').insertAdjacentHTML('afterend',`<div class="alert info">真实公开资料 · 适用性待核准 · ${item.originalUrl?'原始 Word 文件与转换 PDF 分别保存':'保留远程来源；本地 PDF 未取得'}</div><div class="actions">${item.originalUrl?`<a class="btn" href="${escape(item.originalUrl)}" download>下载官方原始文件</a>`:''}<a class="btn" href="${escape(item.sourceUrl)}" target="_blank" rel="noopener">查看官方来源</a></div>`)}
      if(library.tab==='links')reader.insertAdjacentHTML('beforeend',knowledge.usage(item));
    }
    if(id==='S01'){
      const published=versions.findLast(v=>v.status==='已发布'&&v.name==='注册文档 Agent');
      node.querySelector('.agent-launch .tag-row').innerHTML=tag(published?'已发布':'待发布',published?'ok':'warn')+`<span class="muted small">${published?escape(published.id):'暂无可用配置'}</span>`;
      node.querySelector('.agent-launch button').disabled=!published;
    }
    if(id==='S18'){
      const own=tasks.filter(t=>t.projectId===project.id),docs=materials.filter(m=>m.projectId===project.id);
      const stage=!docs.length||docs.some(m=>m.status!=='approved')?1:own.some(t=>t.status==='运行中')?3:own.length?4:2;
      node.querySelectorAll('.journey button').forEach((control,index)=>{control.classList.toggle('current',index===stage);if(index===stage)control.setAttribute('aria-current','step');else control.removeAttribute('aria-current');if(index>=2)control.dataset.value=index===3?'writing':index===4?'review':''});
    }
    if((id==='S09'||id==='S11')&&task.signedRevision===task.revision){
      node.querySelectorAll('[data-action="module-comment"],[data-action="update-source"],[data-action="request-evidence"],[data-action="return-task"],#evidence-confirm').forEach(control=>control.disabled=true);
      const readiness=node.querySelector('#review-readiness');
      if(readiness){readiness.className='review-readiness ready';readiness.textContent=`文档 v${task.revision} 已签发，历史来源和审查记录保持只读。资料更新可在修订版本中处理。`}
    }
    if(id==='S04')node.querySelectorAll('[data-material-row]').forEach(row=>row.setAttribute('aria-selected',String(row.dataset.materialRow===selectedMaterial)));
    if(id==='S05'&&sourceReturn){
      const original=node.querySelector('.source-paper a'),snapshot=material(sourceReturn.materialId).versions.find(v=>v.version===sourceReturn.version);
      if(original){if(snapshot.url)original.href=snapshot.url+(sourceReturn.page?`#page=${sourceReturn.page}`:'');else original.remove()}
    }
    finalProduct.enhance(id,node);
    WorkspaceUI.presentPage(node);
    if(assistant.open)renderAssistant();
    if(navigationReady&&history.state?.workspace&&history.state.screen===id)history.replaceState(navigationState(),'',location.href);
  }
  showDemoProject = row => {
    let found=projects.find(p=>p.row===row);
    if(!found){found={id:`project-${projects.length+1}`,row,name:row[0],product:row[1],category:row[2],path:row[3],status:row[4],owner:'林知远'};projects.unshift(found)}
    project=found;selectedDemoProject=row;
  };
  function navigationState(){return {workspace:true,screen,projectId:project.id,taskId:task.id,chapter,taskParent,setupTaskId,newType,newNote,taskFilter,selectedMaterial,sourceReturn,adminModule:adminList.dataset.activeModule,scrollY:window.scrollY}}
  showScreen = id => {
    const previousUrl=location.href,previousState=history.state,previousScroll=window.scrollY;
    id={S08:'S07',S10:'S09',S12:'S11'}[id]||id;
    selection.hidden=true;
    if(id==='S09'||id==='S11')project=projects.find(p=>p.id===task.projectId);
    if(id==='S04'){sourceReturn=null;if(screen!=='S04')libraryState.mode='list'}
    screen=id;
    managed.forEach(other=>{if(other!==id)document.getElementById(other).replaceChildren()});
    if(managed.has(id))renderScreen(id);baseShowScreen(id);
    if(id==='S03'){
      document.querySelectorAll('#S03 tr[data-record]').forEach(row=>{
        const record=demoProjects[Number(row.querySelector('[data-demo-project]').dataset.demoProject)],selected=projects.find(p=>p.row===record);
        if(!selected)return;
        const docs=materials.filter(m=>m.projectId===selected.id),own=tasks.filter(t=>t.projectId===selected.id);
        row.cells[5].textContent=`项目资料 ${docs.length} 项 · ${docs.filter(m=>m.status==='pending').length} 项待核准`;
        row.cells[6].textContent=own.length?`${own.length} 项任务 · ${own.filter(t=>t.status==='已签发').length} 项已签发`:'尚未创建任务';
      });
    }
    let contextLabel=['S06','S07','S09','S11','S18'].includes(id)?`项目：${project.product}`:'范围：当前企业';
    if(id==='S05'){const item=material(selectedMaterial);contextLabel=item.projectId?`项目：${projects.find(p=>p.id===item.projectId).product}`:item.scope==='mine'?'范围：个人上传区':'范围：企业公共资料'}
    document.querySelector('.topbar .context .chip:nth-child(2)').textContent=contextLabel;
    const company=document.querySelector('.topbar .context .chip:first-child');company.textContent=project.company||'企业名称待配置';company.title=project.publicReference?'公开案例企业：'+project.company:'当前企业';
    WorkspaceUI.presentPage(document.getElementById(id));
    if(!managed.has(id))finalProduct.enhance(id,document.getElementById(id));
    if(assistant.open)renderAssistant();
    const nextUrl=location.href,nextState=navigationState();
    const moved=previousState?.workspace&&(previousUrl!==nextUrl||['screen','projectId','taskId','setupTaskId'].some(key=>previousState[key]!==nextState[key]));
    if(navigationReady&&!restoringNavigation&&moved){history.replaceState({...previousState,scrollY:previousScroll},'',previousUrl);history.pushState(nextState,'',nextUrl)}
    else history.replaceState(nextState,'',nextUrl);
  };
  window.addEventListener('popstate',event=>{
    const saved=event.state;if(!saved?.workspace)return;
    project=projects.find(p=>p.id===saved.projectId);task=tasks.find(t=>t.id===saved.taskId);
    chapter=saved.chapter;taskParent=saved.taskParent;setupTaskId=saved.setupTaskId;newType=saved.newType;newNote=saved.newNote;taskFilter=saved.taskFilter;selectedMaterial=saved.selectedMaterial;sourceReturn=saved.sourceReturn;
    restoringNavigation=true;if(saved.screen==='S02')showAdminModule(saved.adminModule);showScreen(saved.screen);restoringNavigation=false;window.scrollTo(0,saved.scrollY);
  });
  window.addEventListener('hashchange',event=>{if(history.state?.workspace)event.stopImmediatePropagation()},true);
  function renderDocument(review=false){
    const signed=task.signedRevision===task.revision;
    const actions=button('保存正文','save-document','',false,!task.dirty)+button(review?'返回撰写':'进入人工审查','navigate',review?'S09':'S11',!review);
    if(!task.modules.length)return title(review?'人工审查与总稿签发':'文档撰写与来源核验',button('版本记录','document-history')+actions)+flow(review?'S11':'S09')+`<section class="card pad">${empty('任务等待项目资料',`${project.product}的正文尚未准备。请上传项目资料并核准版本。`,button('补充项目资料','project-materials','',true)+button('重新检查资料','resume-task'))}</section>`;
    const current=task.modules[chapter],source=current.sourceId?material(current.sourceId):null,checks=blockers();
    return DocumentReader.render({task,project,review,chapter,source,checks,role,flow:flow(review?'S11':'S09'),title});
  }
  function updateEditorState(){
    const state=document.getElementById('document-save-state');if(state){state.textContent='修改尚未保存';state.className='status warn'}
    document.querySelectorAll('[data-verified-label]').forEach(node=>node.textContent='来源待核验');
    document.querySelectorAll('[data-action="save-document"]').forEach(node=>node.disabled=false);
    document.querySelectorAll('[data-action="confirm-review"],[data-action="sign-document"]').forEach(node=>node.disabled=true);
    const check=document.getElementById('review-readiness');if(check){check.className='review-readiness';check.innerHTML=blockers().map(escape).join('<br>')}
    const review=document.getElementById('review-version-state');if(review)review.textContent='正文已修改，当前版本需要重新审查';
    const context=document.querySelector(`#${screen} .task-context`);if(context)context.outerHTML=taskContext();
    const checkbox=document.getElementById('evidence-confirm');if(checkbox)checkbox.checked=false;
  }
  function sourceScope(item){return item.projectId?projects.find(p=>p.id===item.projectId).product:item.scope==='mine'?'个人上传区':'企业公共资料'}
  function sourceStatus(item){return item.deleted?'回收站':item.status==='approved'?'已核准':item.status==='disabled'?'已停用':item.parsed?'待核准':'等待解析'}
  function inLibraryScope(item){return library.scope==='all'||library.scope==='company'&&item.scope==='company'||library.scope==='project'&&item.projectId===project.id||library.scope==='mine'&&item.author==='林知远'}
  function folderContains(folderId,parentId){if(folderId===parentId)return true;const folder=folders.find(f=>f.id===folderId);return Boolean(folder?.parent&&folderContains(folder.parent,parentId))}
  function libraryFiltered(){
    const view=libraryState.view;
    return materials.filter(m=>(view==='trash'?m.deleted:!m.deleted)
      &&inLibraryScope(m)
      &&(library.category==='all'||m.category===library.category)&&(!libraryState.folder||folderContains(m.folderId,libraryState.folder))
      &&(!library.status||m.status===library.status)&&(!libraryState.tag||m.tags.includes(libraryState.tag))&&(!libraryState.owner||m.author===libraryState.owner)
      &&(view==='favorites'?m.favorite:view==='recent'?libraryState.recent.includes(m.id):view==='pending'?m.status==='pending':view==='disabled'?m.status==='disabled':true)
      &&`${m.name} ${m.text} ${m.version} ${m.source} ${m.tags.join(' ')}`.toLocaleLowerCase().includes(library.query.toLocaleLowerCase()))
      .sort((a,b)=>libraryState.sort==='name'?a.name.localeCompare(b.name,'zh-CN'):view==='recent'?libraryState.recent.indexOf(a.id)-libraryState.recent.indexOf(b.id):b.updated.localeCompare(a.updated));
  }
  function backlinks(id){return tasks.flatMap(t=>t.modules.map((m,index)=>({task:t,module:m,index})).filter(x=>x.module.sourceId===id||x.module.refs?.some(r=>r.id===id)))}
  function sourceReader(item,detail=false){
    const linked=backlinks(item.id),snapshot=sourceReturn?.materialId===item.id?sourceReturn:null;
    const body=snapshot?snapshot.text:item.text,version=snapshot?snapshot.version:item.version,location=snapshot?snapshot.location:item.location;
    const revision=item.versions.find(v=>v.version===version),archived=Boolean(snapshot&&snapshot.version!==item.version),fileUrl=revision?.url;
    const propertyRows=[['归属范围',sourceScope(item)],['文件夹',folders.find(f=>f.id===item.folderId)?.name||categories[item.category]],['来源',item.source],['维护人',item.author],['适用器械类别',item.applicability||'待确认'],['生效日期',item.effective||'未登记'],['复核日期',item.reviewDate||'未登记'],['更新时间',item.updated]];
    const metadata=item.category==='regulations'?[['issuer','发布机关'],['number','文件编号'],['sourceUrl','原文地址'],['publishedDate','发布日期'],['clause','引用条款']]:item.category==='templates'?[['catalog','目录要求'],['mapping','资料来源映射']]:[];
    metadata.forEach(([key,label])=>propertyRows.push([label,item[key]||'未登记']));
    let bodyHTML='';
    if(library.tab==='content')bodyHTML=`<div class="library-reader-layout"><article class="source-paper"><div class="paper-meta">${tag(archived?'历史引用':sourceStatus(item),revision?.status==='approved'?'ok':'warn')}<span>${escape(location)}</span></div><h3>${escape(item.name)}</h3><p>${escape(body)}</p>${fileUrl?`<a href="${escape(fileUrl)}" target="_blank" rel="noopener">打开上传原件</a>`:''}</article><aside class="material-properties"><div class="panel-top"><h3>资料属性</h3>${button('编辑','material-properties',item.id,false,archived||item.deleted)}</div><div class="facts-grid">${propertyRows.map(([label,value])=>`<div><span>${label}</span><strong>${escape(value)}</strong></div>`).join('')}</div><h3>标签</h3><div class="tag-row">${item.tags.map(t=>tag(t,'info')).join('')||'<span class="muted small">尚未设置标签</span>'}</div><h3>核准记录</h3><p class="small">${item.approval?`${escape(item.approval.person)} · ${escape(item.approval.time)}<br>${escape(item.approval.reason)}`:'当前版本等待核准意见。'}</p>${button('查看可见范围','material-access',item.id)}</aside></div>`;
    if(library.tab==='links')bodyHTML=linked.length?`<p class="muted small">资料被以下任务引用。历史任务保留引用版本，更新资料后需要逐项检查影响。</p>`+linked.map(({task:t,module:m,index})=>`<div class="backlink-row"><div><strong>${escape(t.name)} · 文档 v${t.revision}</strong><p>${escape(projects.find(p=>p.id===t.projectId).name)}</p><p>第 ${index+1} 章 ${escape(m.name)} · 引用 ${escape(m.refs?m.refs.filter(r=>r.id===item.id).map(r=>r.version+(r.page?` 第 ${r.page} 页`:'')).join('、'):m.sourceVersion)}</p>${tag(t.status,t.status==='已签发'?'ok':'info')}<p>“${escape(m.text.slice(0,100))}”</p></div>${button('定位正文','backlink',`${t.id}:${index}`)}</div>`).join(''):empty('当前没有任务引用此资料','引用记录按项目、文档和章节自动关联。');
    if(library.tab==='versions')bodyHTML=table(['版本','时间','核准状态','操作'],item.versions.slice().reverse().map(v=>`<tr><td>${escape(v.version)}${v.version===item.version?' · 当前':''}</td><td>${escape(v.updated)}</td><td>${tag(v.status==='approved'?'已核准':v.status==='disabled'?'已停用':'待核准',v.status==='approved'?'ok':'warn')}</td><td>${button('查看正文','material-version',`${item.id}:${v.version}`)}${button('比较版本','compare-material',`${item.id}:${v.version}`)}</td></tr>`));
    if(library.tab==='activity')bodyHTML=item.activity.length?item.activity.map(entry=>`<div class="history-entry"><strong>${escape(entry.action)}</strong><p>${escape(entry.detail)}</p><small>${escape(entry.person)} · ${escape(entry.time)}</small></div>`).join(''):empty('当前会话尚无维护记录','属性修改、核准、版本更新和状态变化将在这里记录。');
    return `<section class="card pad library-reader"><div class="panel-top"><div><h2>${escape(item.name)}</h2><p>${escape(version)} · ${categories[item.category]} · ${linked.length} 处任务引用</p></div><div class="actions">${button('询问 Gru','ask-material',item.id)}${button(item.favorite?'取消收藏':'收藏资料','material-favorite',item.id)}${button('资料操作','material-actions',item.id)}</div></div>${snapshot?`<div class="source-location">${escape(snapshot.label||`从 ${tasks.find(t=>t.id===snapshot.taskId)?.name||'文档'} 第 ${snapshot.chapter+1} 章打开`)} · 固定引用 ${escape(snapshot.version)}${archived?` · 最新资料为 ${escape(item.version)}`:''}</div>`:''}<div class="reader-commands">${item.deleted?button('恢复资料','material-restore',item.id,true):archived?button('查看当前版本','material-current',item.id):button('上传新版本','upload-version',item.id)+button('编辑为新版本','edit-material-content',item.id,false,!item.parsed||item.status==='disabled')+(item.status==='pending'?button('核准版本','approve-material',item.id,true,!item.parsed):tag(sourceStatus(item),item.status==='approved'?'ok':'warn'))}</div><div class="product-tabs" role="tablist">${[['content','正文与属性'],['links',`引用关系 ${linked.length}`],['versions',`版本记录 ${item.versions.length}`],['activity','操作记录']].map(([key,label])=>`<button role="tab" aria-selected="${library.tab===key}" data-action="library-tab" data-value="${key}">${label}</button>`).join('')}</div>${bodyHTML}${detail?'':`<div class="reader-bottom">${button('返回资料列表','library-list')}${button('打开资料详情','material-open',item.id)}</div>`}</section>`;
  }
  function folderTree(parent=null,depth=0){return folders.filter(f=>f.parent===parent).map(f=>`<div class="folder-line" style="--depth:${depth}"><button class="${libraryState.folder===f.id?'active':''}" data-action="folder-open" data-value="${f.id}"><span>${escape(f.name)}</span><small>${materials.filter(m=>!m.deleted&&inLibraryScope(m)&&folderContains(m.folderId,f.id)).length}</small></button>${f.parent?`<button class="folder-edit" data-action="folder-rename" data-value="${f.id}" aria-label="重命名${escape(f.name)}">编辑</button>`:''}</div>${folderTree(f.id,depth+1)}`).join('')}
  function renderLibrary(){
    const supplementary=knowledge.render();
    if(supplementary)return title('资料中心')+knowledge.header()+supplementary;
    const filtered=libraryFiltered(),pages=Math.max(1,Math.ceil(filtered.length/library.size));library.page=Math.min(library.page,pages);
    const visible=filtered.slice((library.page-1)*library.size,library.page*library.size);
    libraryState.selected.forEach(id=>{if(!filtered.some(m=>m.id===id))libraryState.selected.delete(id)});
    const viewNames={all:'全部资料',favorites:'我的收藏',recent:'最近阅读',pending:'待核准',disabled:'已停用',trash:'回收站'};
    const chosen=libraryState.selected.size;
    const nav=`<aside class="library-tree"><h2>资料库</h2><label class="library-scope-label">资料范围<select data-change="library-scope">${options([['all','全部授权资料'],['project','当前项目'],['company','企业公共资料'],['mine','我的上传']],library.scope)}</select></label><div class="scope-project"><strong>${library.scope==='project'?escape(project.product):library.scope==='company'?'企业公共资料':library.scope==='mine'?'林知远的上传':'当前企业'}</strong>${button('选择项目','choose-project')}</div><div class="library-views">${Object.entries(viewNames).map(([key,label])=>`<button class="${libraryState.view===key&&!libraryState.folder?'active':''}" data-action="library-view" data-value="${key}">${label}</button>`).join('')}</div><div class="folder-heading"><h3>分类与文件夹</h3>${button('新建','folder-create')}</div>${folderTree()}<div class="library-side-note">资料、引用版本与人工核准共同维护。文件夹用于整理，访问范围独立管理。</div></aside>`;
    const tabs=`<div class="library-document-tabs"><button class="${libraryState.mode==='list'?'active':''}" data-action="library-list">资料列表</button>${libraryState.opened.map(id=>{const m=material(id);return `<div class="file-tab ${libraryState.mode==='reader'&&selectedMaterial===id?'active':''}"><button data-action="select-material" data-value="${id}" title="${escape(m.name)}">${escape(m.name)}</button><button data-action="close-material-tab" data-value="${id}" aria-label="关闭${escape(m.name)}">关闭</button></div>`}).join('')}</div>`;
    const tags=[...new Set(materials.flatMap(m=>m.tags))],owners=[...new Set(materials.map(m=>m.author))];
    const filters=`<form class="toolbar library-search" data-form="library-search"><label class="library-query">搜索名称、正文或标签<input name="query" type="search" value="${escape(library.query)}" placeholder="在当前范围内检索资料"></label><label>核准状态<select name="status">${options([['','全部状态'],['approved','已核准'],['pending','待核准'],['disabled','已停用']],library.status)}</select></label><button class="btn primary" type="submit">查询</button>${button('重置','library-reset')}<details class="library-more-filters"><summary>更多筛选</summary><div class="toolbar"><label>维护人<select name="owner">${options([['','全部人员'],...owners.map(x=>[x,x])],libraryState.owner)}</select></label><label>标签<select name="tag">${options([['','全部标签'],...tags.map(x=>[x,x])],libraryState.tag)}</select></label><label>排序<select name="sort">${options([['updated','最近更新'],['name','名称顺序']],libraryState.sort)}</select></label></div></details></form>`;
    const batch=`<div class="library-batch"><span>${chosen?`已选择 ${chosen} 项`:'勾选资料进行批量整理'}</span><div>${button('移动文件夹','batch-move','',false,!chosen)}${button('设置标签','batch-tags','',false,!chosen)}${button('导出清单','library-export','',false,!filtered.length)}${libraryState.view==='trash'?button('恢复所选','batch-restore','',false,!chosen):button('加入回收站','batch-trash','',false,!chosen)}</div></div>`;
    const list=visible.length?table([`<input type="checkbox" data-change="library-select-all" aria-label="选择本页全部资料" ${visible.every(m=>libraryState.selected.has(m.id))?'checked':''}>`,'资料名称','版本与状态','归属与维护人','更新时间','引用','操作'],visible.map(m=>`<tr data-material-row="${m.id}"><td><input type="checkbox" data-change="material-select" data-id="${m.id}" aria-label="选择${escape(m.name)}" ${libraryState.selected.has(m.id)?'checked':''}></td><td><button class="file-title" data-action="select-material" data-value="${m.id}">${escape(m.name)}</button><small>${escape(folders.find(f=>f.id===m.folderId)?.name||categories[m.category])}${m.tags.length?' · '+m.tags.map(escape).join('、'):''}</small>${library.query?`<small class="search-hit">${escape(searchExcerpt(m,library.query))}</small>`:''}</td><td>${escape(m.version)}<br>${tag(sourceStatus(m),m.status==='approved'?'ok':'warn')}</td><td>${escape(sourceScope(m))}<small>${escape(m.author)}</small></td><td>${escape(m.updated)}</td><td>${backlinks(m.id).length} 处</td><td>${button(m.favorite?'已收藏':'收藏','material-favorite',m.id)}${button('更多','material-actions',m.id)}</td></tr>`)):empty('没有符合条件的资料',libraryState.view==='trash'?'回收站为空。已被任务或配置使用的资料不能移入回收站。':'调整范围、文件夹或筛选条件，也可以上传资料。');
    const pagination=`<div class="pagination"><span>${filtered.length} 条</span><label>每页 <select data-change="library-size">${options([[10,'10 条'],[20,'20 条'],[50,'50 条']],library.size)}</select></label>${button('上一页','library-page',library.page-1,false,library.page===1)}<span>${library.page} / ${pages}</span>${button('下一页','library-page',library.page+1,false,library.page===pages)}</div>`;
    const path=libraryState.folder?folders.find(f=>f.id===libraryState.folder).name:viewNames[libraryState.view];
    const main=libraryState.mode==='reader'?sourceReader(material(selectedMaterial)):`<section class="card pad library-list-panel"><div class="panel-top"><div><h2>${escape(path)}</h2><p>${filtered.length} 项资料 · ${filtered.filter(m=>m.status==='pending').length} 项待核准</p></div>${button('询问这些资料','ask-library')}</div>${filters}${batch}${list}${pagination}</section>`;
    return title('资料中心',button('批量上传','kw-upload')+button('新建笔记','new-material-note')+button('上传资料','upload-material','',true))+knowledge.header()+`<div class="library-frame">${nav}<div class="library-content">${tabs}${main}</div></div>`;
  }
  function searchExcerpt(item,query){const text=`${item.name}\n${item.text}`;const index=text.toLocaleLowerCase().indexOf(query.toLocaleLowerCase());return index<0?`标签与属性匹配：${query}`:text.slice(Math.max(0,index-20),Math.max(0,index-20)+130)}
  function renderMaterialDetail(){const item=material(selectedMaterial);return title('资料详情',button(sourceReturn?sourceReturn.screen==='S07'?'返回任务配置':'返回引用章节':'返回资料中心',sourceReturn?'source-return':'navigate',sourceReturn?'':'S04'))+sourceReader(item,true)}
  function materialEvent(item,action,detail){item.activity.unshift({action,detail,person:'林知远',time:clockText()});log(action,'',`${item.name} · ${detail}`)}
  function materialImpact(item){return backlinks(item.id).filter(({task:t})=>t.status!=='已签发')}
  function invalidateMaterial(item){materialImpact(item).forEach(({task:t})=>{t.reviewRevision=null;t.verified={};t.status='待人工审查';t.modules.forEach(m=>m.refs?.filter(r=>r.id===item.id).forEach(r=>r.checked=false))})}
  function openMaterial(id){selectedMaterial=id;sourceReturn=null;library.tab='content';libraryState.mode='reader';if(!libraryState.opened.includes(id))libraryState.opened.push(id);libraryState.recent=[id,...libraryState.recent.filter(x=>x!==id)];renderScreen('S04')}
  function resetLibraryView(){libraryState.mode='list';libraryState.selected.clear();library.page=1;sourceReturn=null}
  function materialReferences(item){return backlinks(item.id).length+versions.filter(v=>v.template===item.id||v.rule===item.id).length+knowledge.references(item.id)+dossier.references(item.id)}
  function propertyDialog(item){
    openDialog('编辑资料属性',`<div class="form two"><label class="field wide">资料名称<input name="name" required value="${escape(item.name)}"></label><label class="field">维护人<select name="author">${options(['林知远','周予安','沈知微','顾明川'].map(x=>[x,x]),item.author)}</select></label><label class="field">标签<input name="tags" value="${escape(item.tags.join('，'))}" placeholder="使用逗号分隔"></label><label class="field wide">来源说明<input name="source" required value="${escape(item.source)}"></label><label class="field">适用器械类别<select name="applicability">${options(['待确认','第二类','第三类','第二类与第三类'].map(x=>[x,x]),item.applicability||'待确认')}</select></label><label class="field">生效日期<input name="effective" type="date" value="${escape(item.effective||'')}"></label><label class="field">复核日期<input name="reviewDate" type="date" value="${escape(item.reviewDate||'')}"></label></div><p class="muted small">归属范围：${escape(sourceScope(item))}。此处维护属性；正文修改通过新版本保存。</p>`,'保存属性',values=>{
      const name=values.get('name').trim();if(!name){dialogError('请填写资料名称。');return}
      if(materials.some(m=>m.id!==item.id&&!m.deleted&&m.scope===item.scope&&m.projectId===item.projectId&&m.name===name)){dialogError('当前归属中已有同名资料。');return}
      const governed=['source','applicability','effective','reviewDate','issuer','number','sourceUrl','publishedDate','clause','catalog','mapping'];
      const changed=governed.some(key=>values.has(key)&&String(item[key]||'')!==String(values.get(key)||''));
      Object.assign(item,{name,author:values.get('author'),source:values.get('source'),tags:readTags(values.get('tags')),applicability:values.get('applicability'),effective:values.get('effective'),reviewDate:values.get('reviewDate')});
      governed.forEach(key=>{if(values.has(key))item[key]=values.get(key)});
      if(changed&&item.status==='approved'){item.status='pending';item.versions.at(-1).status='pending';item.approval=null;invalidateMaterial(item)}
      materialEvent(item,'更新资料属性','名称、维护人、标签和适用信息已保存');closeProductDialog();renderScreen(screen);
    });
    const extraFields=item.category==='regulations'?[['issuer','发布机关','text'],['number','文件编号','text'],['sourceUrl','原文地址','url'],['publishedDate','发布日期','date'],['clause','引用条款与位置','text']]:item.category==='templates'?[['catalog','目录要求','text'],['mapping','目录与资料来源映射','text']]:[];
    extraFields.forEach(([name,label,type])=>{const field=document.createElement('label');field.className='field wide';field.textContent=label;const input=document.createElement('input');input.name=name;input.type=type;input.value=item[name]||'';field.append(input);dialog.querySelector('.form').append(field)});
    const note=document.createElement('p');note.className='muted small';note.textContent='修改来源或适用信息后，已核准资料需要重新核准。';dialog.querySelector('.dialog-body').append(note);
  }
  const readTags=value=>[...new Set(value.split(/[,，]/).map(x=>x.trim()).filter(Boolean))];
  function noteDialog(item=null){
    openDialog(item?'编辑资料并保存新版本':'新建资料笔记',`<div class="form two"><label class="field wide">资料名称<input name="name" required value="${escape(item?.name||'')}" ${item?'readonly':''}></label><label class="field">归属范围<select name="scope" ${item?'disabled':''}>${options([['project','当前项目'],['company','企业公共资料'],['mine','个人上传区']],item?.scope||'project')}</select></label><label class="field">版本名称<input name="version" required value="${item?escape(item.version)+'.1':'v1.0'}"></label><label class="field wide">来源说明<input name="source" required value="${escape(item?.source||'')}"></label><label class="field wide">资料正文<textarea name="text" rows="12" required>${escape(item?.text||'')}</textarea></label></div><p class="muted small">保存后进入待核准。既有任务保留引用版本，已签发文稿保持只读。</p>`,'保存待核准版本',values=>{
      if(item?.versions.some(v=>v.version===values.get('version'))){dialogError('此版本名称已存在。');return}
      const name=values.get('name').trim(),scope=item?.scope||values.get('scope');
      if(!name||!values.get('text').trim()){dialogError('名称和正文需要有效内容。');return}
      if(!item&&materials.some(m=>!m.deleted&&m.name===name&&m.scope===scope&&(scope!=='project'||m.projectId===project.id))){dialogError('当前范围已有同名资料，请打开资料创建新版本。');return}
      const category=item?.category||(library.category!=='all'?library.category:'project');
      const target=item||{id:`material-${materials.length}`,name,scope,projectId:scope==='project'?project.id:null,category,folderId:libraryState.folder||category,tags:[],favorite:false,deleted:false,activity:[],versions:[],author:'林知远'};
      if(!item)materials.push(target);
      Object.assign(target,{text:values.get('text'),source:values.get('source'),version:values.get('version'),status:'pending',parsed:true,location:'资料笔记 · 全文',updated:new Date().toLocaleDateString('sv-SE'),url:null,approval:null});
      target.versions.push({version:target.version,text:target.text,status:'pending',location:target.location,updated:target.updated});invalidateMaterial(target);materialEvent(target,item?'保存资料新版本':'创建资料笔记',target.version);closeProductDialog();selectedMaterial=target.id;sourceReturn=null;library.tab='content';setScreen('S05');
    });
  }
  function handleLibraryAction(action,value){
    if(action==='library-list'){libraryState.mode='list';sourceReturn=null;renderScreen('S04');return true}
    if(action==='library-view'){libraryState.view=value;libraryState.folder='';library.category='all';library.status='';library.query='';resetLibraryView();renderScreen('S04');return true}
    if(action==='folder-open'){libraryState.folder=value;libraryState.view='all';library.category=folders.find(f=>f.id===value).category;resetLibraryView();renderScreen('S04');return true}
    if(action==='folder-create'||action==='folder-rename'){
      const existing=action==='folder-rename'?folders.find(f=>f.id===value):null;
      openDialog(existing?'重命名文件夹':'新建文件夹',`<div class="form"><label class="field">文件夹名称<input name="name" required value="${escape(existing?.name||'')}"></label>${existing?'':`<label class="field">上级文件夹<select name="parent">${options(folders.map(f=>[f.id,f.name]),libraryState.folder||'project')}</select></label>`}</div>${existing?button('删除空文件夹','folder-delete',existing.id):''}`,'保存文件夹',values=>{
        const parent=existing?.parent||values.get('parent'),name=values.get('name').trim();if(!name){dialogError('请填写文件夹名称。');return}
        if(folders.some(f=>f.id!==existing?.id&&f.parent===parent&&f.name===name)){dialogError('上级文件夹中已存在此名称。');return}
        if(existing)existing.name=name;else folders.push({id:`folder-${folders.length+1}`,name,parent,category:folders.find(f=>f.id===parent).category});closeProductDialog();renderScreen('S04');
      });return true;
    }
    if(action==='folder-delete'){
      if(materials.some(m=>m.folderId===value)||folders.some(f=>f.parent===value)){dialogError('文件夹仍包含资料或子文件夹，请移动内容后重试。');return true}
      folders.splice(folders.findIndex(f=>f.id===value),1);if(libraryState.folder===value)libraryState.folder='';closeProductDialog();renderScreen('S04');return true;
    }
    if(action==='close-material-tab'){libraryState.opened=libraryState.opened.filter(id=>id!==value);if(selectedMaterial===value)libraryState.mode='list';renderScreen('S04');return true}
    if(action==='material-favorite'){const item=material(value);item.favorite=!item.favorite;renderScreen(screen);return true}
    if(action==='new-material-note'){noteDialog();return true}
    if(action==='edit-material-content'){noteDialog(material(value));return true}
    if(action==='material-properties'){propertyDialog(material(value));return true}
    if(action==='material-access'){
      const item=material(value);openDialog('资料可见范围',`<h3>${escape(item.name)}</h3><div class="facts-grid"><div><span>归属</span><strong>${escape(sourceScope(item))}</strong></div><div><span>读取范围</span><strong>${item.scope==='mine'?'上传者本人':item.projectId?'当前项目获授权成员':'当前企业获授权成员'}</strong></div><div><span>维护与核准</span><strong>依据企业岗位授权</strong></div></div><p>项目资料不会因移动文件夹而改变访问范围。正式权限由企业与项目授权管理。</p>`,'完成查看');return true;
    }
    if(action==='material-actions'){
      const item=material(value);openDialog('资料操作',`<strong>${escape(item.name)}</strong><p>${escape(item.version)} · ${sourceStatus(item)} · ${materialReferences(item)} 处任务或配置关联</p><div class="material-action-grid">${button('编辑属性','dialog-material-properties',item.id,false,item.deleted)}${button('移动文件夹','dialog-material-move',item.id,false,item.deleted)}${button('导出正文','material-export',item.id,false,!item.parsed)}${button('查看可见范围','dialog-material-access',item.id)}${item.deleted?button('恢复资料','dialog-material-restore',item.id):item.status==='disabled'?button('恢复为待核准','dialog-material-enable',item.id):button('停用资料','dialog-material-disable',item.id)}${item.deleted?'':button('加入回收站','dialog-material-trash',item.id)}</div>`,'完成查看');return true;
    }
    if(action.startsWith('dialog-material-')){closeProductDialog();return handleLibraryAction(action.replace('dialog-',''),value)}
    if(action==='material-move'||action==='batch-move'){
      const items=action==='material-move'?[material(value)]:[...libraryState.selected].map(material);
      const choices=folders.filter(f=>items.every(m=>m.category===f.category));if(!choices.length){showFeedback('请选取相同资料类别，再移动到对应文件夹。');return true}
      openDialog('移动到文件夹',`<p>${items.length} 项资料，项目归属与授权范围保持当前设置。</p><label class="field">目标文件夹<select name="folder">${options(choices.map(f=>[f.id,f.name]),items[0].folderId)}</select></label>`,'确认移动',values=>{items.forEach(m=>{m.folderId=values.get('folder');materialEvent(m,'移动文件夹',folders.find(f=>f.id===m.folderId).name)});closeProductDialog();renderScreen(screen)});return true;
    }
    if(action==='batch-tags'){
      openDialog('设置所选资料标签',`<p>已选择 ${libraryState.selected.size} 项资料。</p><label class="field">追加标签<input name="tags" required placeholder="多个标签使用逗号分隔"></label>`,'保存标签',values=>{const tags=readTags(values.get('tags'));if(!tags.length){dialogError('请填写标签。');return}libraryState.selected.forEach(id=>{const item=material(id);item.tags=[...new Set([...item.tags,...tags])];materialEvent(item,'设置标签',tags.join('、'))});closeProductDialog();renderScreen('S04')});return true;
    }
    if(['material-disable','material-enable','material-trash','batch-trash','material-restore','batch-restore'].includes(action)){
      const items=action.startsWith('batch')?[...libraryState.selected].map(material):[material(value)],deleting=action.endsWith('trash'),restoring=action.endsWith('restore'),enabling=action.endsWith('enable');
      const blocked=deleting?items.filter(m=>materialReferences(m)>0):[];
      openDialog(deleting?'移入回收站':restoring?'恢复资料':enabling?'恢复为待核准':'停用资料',`<p>${items.map(m=>escape(m.name)).join('、')}</p>${blocked.length?`<div class="alert error">${blocked.map(m=>escape(m.name)).join('、')}正在被任务或配置引用，不能移入回收站。需要停止使用时请选择停用资料。</div>`:`<p>${deleting?'资料将从正常列表隐藏，可以从回收站恢复。':restoring||enabling?'恢复后进入待核准，需要重新确认适用版本。':`${items.reduce((n,m)=>n+materialImpact(m).length,0)} 处待审引用需要重新处理。已签发版本保留历史记录。`}</p><label class="field">处理原因<textarea name="reason" required></textarea></label>`}` ,blocked.length?'完成查看':'确认处理',blocked.length?null:values=>{
        items.forEach(item=>{if(deleting)item.deleted=true;else{item.deleted=false;item.status=restoring||enabling?'pending':'disabled';item.versions.at(-1).status=item.status;item.approval=null;invalidateMaterial(item)}materialEvent(item,deleting?'移入回收站':restoring?'恢复资料':enabling?'恢复待核准':'停用资料',values.get('reason'))});libraryState.selected.clear();closeProductDialog();renderScreen(screen);
      });return true;
    }
    if(action==='material-export'){const item=material(value);downloadFile(`${item.name}-${item.version}.txt`,`${item.name}\n版本：${item.version}\n归属：${sourceScope(item)}\n状态：${sourceStatus(item)}\n来源：${item.source}\n\n${item.text}`);return true}
    if(action==='library-export'){const items=libraryState.selected.size?[...libraryState.selected].map(material):libraryFiltered();downloadFile('资料清单.json',JSON.stringify(items.map(m=>({name:m.name,version:m.version,status:sourceStatus(m),scope:sourceScope(m),folder:folders.find(f=>f.id===m.folderId)?.name,source:m.source,tags:m.tags,owner:m.author,references:materialReferences(m)})),null,2));return true}
    if(action==='material-current'){sourceReturn=null;library.tab='content';renderScreen(screen);return true}
    if(action==='material-version'){const [id,version]=value.split(':'),item=material(id),old=item.versions.find(v=>v.version===version);sourceReturn={materialId:id,version,text:old.text,location:old.location,label:'历史版本阅读'};library.tab='content';renderScreen(screen);return true}
    return false;
  }
  function uploadDialog(versionId=''){
    const existing=versionId?material(versionId):null;
    openDialog(existing?'上传资料新版本':'上传资料',`<p class="muted small">文件保留在当前浏览器会话。TXT、Markdown 可读取正文；其他文档保留原件并显示等待解析。</p><div class="form two"><label class="field wide">资料文件<input name="file" type="file" required accept=".txt,.md,.pdf,.docx"></label><label class="field">归属范围<select name="scope" ${existing?'disabled':''}>${options([['project','当前项目'],['company','企业公共资料'],['mine','个人上传区']],existing?.scope||'project')}</select></label><label class="field">资料类别<select name="category">${options(Object.entries(categories),existing?.category||'project')}</select></label><label class="field">版本名称<input name="version" required value="${existing?`${existing.version}.1`:'v1.0'}"></label><label class="field">来源说明<input name="source" required placeholder="提供人员或文件来源"></label><label class="field wide">重复文件处理<select name="duplicate"><option value="review">发现同名文件时停止并提示</option><option value="version">作为同名文件的新版本</option></select></label></div>`,existing?'保存新版本':'上传到当前会话',async values=>{
      const file=values.get('file');if(!(file instanceof File)||!file.size){dialogError('请选择非空文件。');return}
      if(file.size>5*1024*1024){dialogError('当前设计稿支持 5MB 以内的文件。');return}
      const targetScope=existing?.scope||values.get('scope');
      let target=existing||materials.find(m=>m.name===file.name&&m.scope===targetScope&&(targetScope!=='project'||m.projectId===project.id));
      if(target&&!existing&&values.get('duplicate')==='review'){dialogError('已存在同名资料，请选择作为新版本处理，或更换文件。');return}
      if(target?.versions.some(v=>v.version===values.get('version'))){dialogError('此版本名称已存在，请填写新的版本名称。');return}
      const readable=/\.(txt|md)$/i.test(file.name);const text=readable?await file.text():'当前浏览器已保存原件，正文解析需要文档服务。';
      if(!target){target={id:`material-${materials.length}`,name:file.name,scope:targetScope,projectId:targetScope==='project'?project.id:null,versions:[],tags:[],favorite:false,deleted:false,activity:[]};materials.push(target)}
      Object.assign(target,{category:values.get('category'),version:values.get('version'),source:values.get('source'),author:'林知远',status:'pending',parsed:readable,text,location:'上传原件 · 全文',updated:new Date().toLocaleDateString('sv-SE'),url:URL.createObjectURL(file),uploaded:true});
      target.versions.push({version:target.version,text,status:'pending',location:target.location,updated:target.updated,url:target.url});
      target.folderId=target.folderId||target.category;target.approval=null;target.deleted=false;
      imports.unshift({id:target.id,name:file.name,status:readable?'正文已读取，等待核准':'原件已接收，等待解析',readable});
      backlinks(target.id).filter(({task:t})=>t.status!=='已签发').forEach(({task:t})=>{t.reviewRevision=null;t.verified={};t.status='待人工审查'});
      materialEvent(target,'上传资料版本',target.version);selectedMaterial=target.id;sourceReturn=null;library.tab='content';closeProductDialog();setScreen('S05');
    });
  }
  function approveMaterial(id){
    const item=material(id);
    if(item.origin&&(!item.origin.evidence.length||item.origin.evidence.some(source=>source.id===item.id||material(source.id).origin||!knowledge.available(source)))){showFeedback('成果需要可核查的原始证据；来源不足或循环引用时不能核准。');return}
    if(!item.parsed||item.deleted||item.status==='disabled'){showFeedback('当前资料不可核准，请先完成解析或恢复为待核准。');return}
    const missing=item.category==='regulations'?[['issuer','发布机关'],['number','文件编号'],['sourceUrl','原文地址'],['publishedDate','发布日期'],['effective','生效日期'],['reviewDate','复核日期'],['clause','引用条款']].filter(([key])=>!item[key]):[];
    if(missing.length){openDialog('补全法规来源信息',`<p>${escape(item.name)}</p><div class="alert">尚缺少：${missing.map(([,label])=>label).join('、')}。补全资料并核对原文后才能核准。</div>${button('编辑来源属性','dialog-material-properties',id,true)}`,'完成查看');return}
    openDialog('核准资料版本',`<strong>${escape(item.name)} ${escape(item.version)}</strong><p>核准范围：${item.scope==='project'?escape(projects.find(p=>p.id===item.projectId).name):'企业公共资料'}</p><div class="form"><label><input type="checkbox" name="read" required> 已检查原文与引用位置</label><label><input type="checkbox" name="scope" required> 已核对版本和项目适用范围</label><label class="field">核准意见<textarea name="reason" required></textarea></label></div>`,'核准当前版本',values=>{
      item.status='approved';item.versions.at(-1).status='approved';item.approval={person:'沈知微',reason:values.get('reason'),time:clockText()};item.versions.at(-1).approval={...item.approval};materialEvent(item,'核准资料',`${item.version} · ${values.get('reason')}`);closeProductDialog();renderScreen(screen);
    });
  }
  function pageAgentContext(){
    if(['S09','S11'].includes(screen))return {key:`task:${task.id}:${task.revision}:${chapter}`,type:'task',label:`${project.product} · ${task.name} · 第 ${chapter+1} 章`,taskId:task.id,projectId:task.projectId,revision:task.revision,chapter,materialIds:[...new Set(task.modules.flatMap(m=>[m.sourceId,...(m.refs||[]).map(r=>r.id)]).filter(Boolean))]};
    if(screen==='S05'||screen==='S04'&&libraryState.mode==='reader'){
      const item=material(selectedMaterial),snapshot=sourceReturn?.materialId===item.id?sourceReturn:null;
      return {key:`material:${item.id}:${snapshot?.version||item.version}`,type:'material',materialId:item.id,label:`${item.name} · ${snapshot?.version||item.version}`,projectId:item.projectId,materialIds:[item.id],sourceVersion:snapshot?.version||item.version};
    }
    if(screen==='S04')return {key:`library:${library.scope}:${project.id}:${libraryState.folder}:${libraryState.view}:${library.query}`,type:'library',label:`资料中心 · ${libraryFiltered().length} 项当前资料`,projectId:library.scope==='project'?project.id:null,materialIds:libraryFiltered().map(m=>m.id)};
    if(['S06','S07','S18'].includes(screen))return {key:`project:${project.id}`,type:'project',label:project.name,projectId:project.id,materialIds:materials.filter(m=>!m.deleted&&(m.projectId===project.id||m.scope==='company')).map(m=>m.id)};
    return {key:`page:${screen}`,type:screen==='S17'?'restricted':'page',label:screen==='S17'?'访问受限':document.querySelector(`#${screen} h1`)?.textContent||'当前页面',materialIds:[]};
  }
  function activeConversation(){return conversations.find(c=>c.id===assistant.current)}
  function newConversation(context=pageAgentContext()){
    context.sourceSnapshots=contextSources(context);
    const conversation={id:`conversation-${++assistant.sequence}`,title:context.label,context:{...context,materialIds:[...context.materialIds]},messages:[],created:clockText()};
    conversations.unshift(conversation);assistant.current=conversation.id;assistant.history=false;assistant.draft='';assistant.mode='question';return conversation;
  }
  function showAssistant(context=null){
    if(context)newConversation(context);else if(!assistant.current)newConversation();
    assistant.open=true;assistantPanel.hidden=false;assistantLauncher.setAttribute('aria-expanded','true');document.body.classList.add('agent-open');renderAssistant();assistantPanel.querySelector('textarea')?.focus();
  }
  function hideAssistant(){assistant.open=false;assistantPanel.hidden=true;document.body.classList.remove('agent-open');assistantLauncher.setAttribute('aria-expanded','false');assistantLauncher.focus()}
  function messageCard(card,value){return `<button class="agent-action-card" data-action="assistant-card" data-value="${value}"><strong>${escape(card.title)}</strong><span>${escape(card.description||'查看关联记录')}</span><small>打开处理</small></button>`}
  function renderAssistant(){
    if(!assistant.open)return;
    const conversation=activeConversation(),context=conversation.context,current=pageAgentContext(),changed=current.key!==context.key;
    assistantPanel.classList.toggle('expanded',assistant.expanded);
    const history=assistant.history?`<section class="assistant-history"><h3>会话记录</h3>${conversations.map(c=>`<button class="${c.id===assistant.current?'active':''}" data-action="assistant-session" data-value="${c.id}"><strong>${escape(c.title)}</strong><small>${escape(c.created)} · ${c.messages.length} 条记录</small></button>`).join('')}</section>`:'';
    const messages=conversation.messages.map((m,index)=>`<article class="chat-message ${m.role}" data-chat-message="${index}"><div class="chat-message-label">${m.role==='user'?'用户':`${gruAvatar('message')}Gru`}${m.role==='user'?'':`<span>${escape(m.nature==='data'?'页面数据查询':m.nature==='proposal'?'修订预览 · 人工提供修订':'模型未连接')}</span>`}</div><div class="chat-message-text">${escape(m.text)}</div>${m.citations?.length?`<div class="chat-citations">${m.citations.map((c,sourceIndex)=>`<button data-action="assistant-source" data-value="${index}:${sourceIndex}"><strong>${escape(c.name)} ${escape(c.version)}</strong><span>${escape(c.location)}</span><q>${escape(c.text.slice(0,180))}</q></button>`).join('')}</div>`:''}${m.cards?.map((card,cardIndex)=>messageCard(card,`${index}:${cardIndex}`)).join('')||''}${m.proposal?`<div class="chat-proposal"><details><summary>查看修改前后内容</summary><h4>当前正文</h4><p>${escape(m.proposal.before)}</p><h4>拟替换内容</h4><p>${escape(m.proposal.after)}</p></details>${m.applied?tag('已写入，等待保存','ok'):button('确认写入当前章节','assistant-apply',index,true)}<p class="muted small">写入后重新核验来源和审查结果。</p></div>`:''}</article>`).join('');
    const welcome=`<div class="assistant-welcome">${gruAvatar('welcome','Gru，拟人男性对话助手')}<h3>你好，我是 Gru</h3><p>围绕当前项目、资料和正文交流，相关依据与处理入口会保留在对话中。</p><div class="assistant-suggestions">${[['summary','查看当前工作摘要'],['sources','查看关联资料与来源'],['review','检查待处理事项']].map(([id,label])=>button(label,'assistant-suggestion',id)).join('')}</div></div>`;
    assistantPanel.innerHTML=`<header class="assistant-header"><div class="assistant-identity">${gruAvatar('header')}<div><strong>Gru</strong><span>随时提问 · 保留工作现场</span></div></div><div>${button(assistant.expanded?'收起阅读区':'展开阅读区','assistant-expand')}${button('关闭','assistant-close')}</div></header><div class="assistant-tools">${button('新建对话','assistant-new')}${button('历史会话','assistant-history')}${button('导出对话','assistant-export')}</div>${history}<section class="assistant-context"><span>本次对话范围</span><strong>${escape(context.label)}</strong><div>${tag(`${context.materialIds.length} 项资料`,'info')}${context.revision?tag(`文档 v${context.revision}`):''}${button('选择资料','assistant-attach')}</div>${context.quote?`<blockquote>${escape(context.quote)}</blockquote>`:''}${changed?`<div class="context-change">当前页面已变化，本次对话保留原来的范围。${button('围绕当前页面新建对话','assistant-new')}</div>`:''}</section><div class="assistant-messages" role="log" aria-live="polite">${messages||welcome}</div><form id="assistant-composer" class="assistant-composer"><label class="sr-only" for="assistant-prompt">向 Gru 提问</label><textarea id="assistant-prompt" name="prompt" rows="3" required placeholder="向 Gru 提问，或说明需要如何修改…">${escape(assistant.draft)}</textarea><div class="assistant-send-row"><label>处理方式<select name="mode"><option value="question">对话提问</option><option value="search">检索资料</option><option value="review">检查当前任务</option><option value="edit">提出修改要求</option></select></label><button class="btn primary" type="submit">发送</button></div><p>Enter 发送，Shift + Enter 换行。页面查询可用；生成回答需连接模型。</p></form>`;
    assistantPanel.querySelector('#assistant-composer select').value=assistant.mode;
    assistantPanel.querySelector('#assistant-composer>p').textContent='Enter 发送，Shift + Enter 换行。';
    assistantPanel.querySelectorAll('.chat-message-label>span').forEach((node,index)=>{const replies=conversation.messages.filter(message=>message.role!=='user');node.textContent=replies[index]?.nature==='proposal'?'修订预览':'来源与处理记录'});
    assistantPanel.querySelector('.assistant-context>div').insertAdjacentHTML('beforeend',button('来源策略','research-assistant-scope'));
    assistantPanel.querySelector('.assistant-tools').insertAdjacentHTML('beforeend',button('会话附件','research-attachment')+button('写作习惯','research-preferences'));
    assistantPanel.querySelector('.assistant-context').insertAdjacentHTML('beforeend',`<p class="muted small">${context.sourcing?.network?'允许指定网络来源':'联网关闭'} · ${context.sourcing?.mode==='conversation'?'仅处理对话内容':`固定资料版本 ${context.materialIds.length} 项`}</p>`);
    const list=assistantPanel.querySelector('.assistant-messages');list.scrollTop=list.scrollHeight;
  }
  function contextSources(context){
    if(context.sourceSnapshots)return context.sourceSnapshots.filter(source=>{const item=material(source.id);return !item.deleted&&item.status!=='disabled'});
    if(context.taskId){const currentTask=tasks.find(t=>t.id===context.taskId);const sources=currentTask.modules.flatMap((m,index)=>(m.refs||[{id:m.sourceId,name:material(m.sourceId).name,version:m.sourceVersion,text:m.sourceText,location:m.location}]).filter(r=>context.materialIds.includes(r.id)).map(r=>({...r,taskId:currentTask.id,chapter:index})));const extra=context.materialIds.filter(id=>!sources.some(s=>s.id===id)).map(material).filter(m=>!m.deleted).map(m=>({id:m.id,name:m.name,version:m.version,text:m.text,location:m.location}));return [...sources,...extra]}
    return context.materialIds.map(material).filter(m=>!m.deleted).map(m=>{const revision=context.materialId===m.id?m.versions.find(v=>v.version===context.sourceVersion):m.versions.at(-1);return {id:m.id,name:m.name,version:revision.version,text:revision.text,location:revision.location}});
  }
  function sendAssistant(prompt,mode='question'){
    if(!prompt.trim())return;
    const conversation=activeConversation(),context=conversation.context;assistant.mode=mode;
    conversation.messages.push({role:'user',text:prompt,scopeSnapshot:structuredClone({materials:contextSources(context),mode:context.sourcing?.mode||'approved',network:context.sourcing?.network||false,urls:context.sourcing?.urls||'',terms:context.sourcing?.terms||'',projectId:context.projectId,revision:context.revision})});if(conversation.messages.length===1)conversation.title=prompt.slice(0,30);assistant.draft='';
    const currentTask=context.taskId?tasks.find(t=>t.id===context.taskId):null;
    let reply={role:'assistant',nature:'data',text:'',cards:[],citations:[]};
    if(context.type==='restricted'){reply.text='当前页面没有资料访问权限。请返回已授权的项目或联系企业管理员。';conversation.messages.push(reply);renderAssistant();return}
    if(currentTask&&currentTask.revision!==context.revision){reply.text=`此对话对应文档 v${context.revision}，当前文档为 v${currentTask.revision}。请围绕当前版本新建对话后继续处理。`;conversation.messages.push(reply);renderAssistant();return}
    if(mode==='search'){
      const available=contextSources(context);
      const matches=available.filter(m=>`${m.name} ${m.text} ${material(m.id).tags.join(' ')}`.toLocaleLowerCase().includes(prompt.trim().toLocaleLowerCase()));
      reply.text=matches.length?`在本次对话范围中找到 ${matches.length} 项资料。以下展示前 6 项匹配内容，打开资料可以核对版本与适用范围。`:'本次范围没有匹配资料。请调整关键词，或通过“选择资料”增加检索范围。';
      reply.citations=matches.slice(0,6).map(m=>({id:m.id,name:m.name,version:m.version,text:searchExcerpt(m,prompt),fullText:m.text,location:m.location}));
    }else if(mode==='edit'||/修改|改写|润色|补写/.test(prompt)){
      reply.nature='service';reply.text=currentTask?`修改范围已定位到第 ${context.chapter+1} 章。可以编辑替换文字、比较修改内容，确认后写入当前文稿。`:'请打开需要编辑的文档或选中文字，修改内容会与对应文稿版本共同保存。';
      if(currentTask&&currentTask.signedRevision===currentTask.revision)reply.text='当前文档已签发并保持只读。请在文档页面创建修订版本后继续。';
      else if(currentTask)reply.cards=[{action:'provide-revision',title:'填写修订并预览',description:`${currentTask.name} · 第 ${context.chapter+1} 章 · 文档 v${context.revision}`}];
    }else if(/签发|核准|停用|删除/.test(prompt)&&!(/待|哪些|查看|检查/.test(prompt))){
      reply.text='该动作需要核对对象、版本和影响后确认。请打开对应记录，查看处理条件。';
      reply.cards=currentTask?[{action:'task',taskId:currentTask.id,title:'打开人工审查',description:`${currentTask.name} · ${currentTask.status}`}]:context.materialIds.slice(0,3).map(id=>({action:'material',materialId:id,title:material(id).name,description:sourceStatus(material(id))}));
    }else if(mode==='review'||/待处理|待办|阻塞|缺少|检查/.test(prompt)){
      const items=currentTask?[currentTask]:tasks.filter(t=>(!context.projectId||t.projectId===context.projectId)&&t.status!=='已签发');
      reply.text=items.length?items.map(t=>`${t.name} · ${t.status}\n负责人：${t.owner}；未核验章节：${t.modules.filter((m,i)=>t.verified[i]!==t.revision).length}；未解决批注：${t.comments.filter(c=>!c.resolved).length}。${t.dirty?'正文尚未保存。':''}`).join('\n\n'):'当前范围没有待处理任务。';
      reply.cards=items.slice(0,4).map(t=>({action:'task',taskId:t.id,title:`处理${t.name}`,description:`${t.owner} · 文档 v${t.revision}`}));
    }else if(/摘要|概况|当前工作|来源|资料|依据|引用/.test(prompt)){
      const sources=contextSources(context);reply.citations=sources.slice(0,6);
      reply.text=currentTask?`当前任务：${currentTask.name}\n当前状态：${currentTask.status}；负责人：${currentTask.owner}。\n文档 v${context.revision}，共 ${currentTask.modules.length} 章。以下资料来自此任务固定的引用版本。`:sources.length?`本次范围包含 ${sources.length} 项资料。以下为原文摘录与版本，可打开核对。`:'当前对话尚未附加资料。可以选择资料，或进入项目、文档后围绕当前页面新建对话。';
    }else{const sources=contextSources(context).slice(0,3);reply.nature='data';reply.text=sources.length?`当前资料中的相关内容：\n\n${sources.map(source=>`${source.name}\n${source.text.slice(0,300)}`).join('\n\n')}\n\n可以打开引用原文核对版本与适用范围。`:'本次对话尚未选择资料。请添加文档或图片，明确需要回答的问题。';reply.citations=sources}
    if(currentTask){research.recordEvent(currentTask,'Gru 对话',`文稿 v${context.revision} · ${context.questionIndex===undefined?'当前章节':currentTask.research.questions[context.questionIndex].question}：${prompt}`);reply.cards.push({action:'record-request',title:'登记为待处理要求',description:'确认后关联任务与章节，正文保持当前内容',request:prompt,taskId:currentTask.id,revision:currentTask.revision,questionIndex:context.questionIndex})}
    conversation.messages.push(reply);renderAssistant();assistantPanel.querySelector('textarea').focus();
  }
  function handleAssistantAction(action,value){
    if(action==='assistant-toggle'){if(assistant.open)hideAssistant();else showAssistant();return true}
    if(action==='assistant-close'){hideAssistant();return true}
    if(action==='assistant-expand'){assistant.expanded=!assistant.expanded;renderAssistant();return true}
    if(action==='assistant-new'){newConversation();renderAssistant();assistantPanel.querySelector('textarea').focus();return true}
    if(action==='assistant-history'){assistant.history=!assistant.history;renderAssistant();return true}
    if(action==='assistant-session'){assistant.current=value;assistant.history=false;assistant.draft='';renderAssistant();return true}
    if(action==='assistant-export'){downloadFile('Gru 对话记录.json',JSON.stringify(activeConversation(),null,2));return true}
    if(action==='assistant-suggestion'){const prompts={summary:'查看当前工作摘要',sources:'查看关联资料与来源',review:'检查待处理事项'};sendAssistant(prompts[value]);return true}
    if(action==='ask-material'){const item=material(value),snapshot=sourceReturn?.materialId===item.id?sourceReturn:null;showAssistant({key:`material:${item.id}:${snapshot?.version||item.version}`,type:'material',materialId:item.id,label:`${item.name} · ${snapshot?.version||item.version}`,projectId:item.projectId,materialIds:[item.id],sourceVersion:snapshot?.version||item.version});return true}
    if(action==='ask-library'){showAssistant(pageAgentContext());return true}
    if(action==='ask-selection'){selection.hidden=true;const context=pageAgentContext();context.chapter=selectedModule;context.quote=selectedQuote;context.label=`${task.name} · 第 ${selectedModule+1} 章选中文字`;context.key=`task:${task.id}:${task.revision}:${selectedModule}`;showAssistant(context);return true}
    if(action==='assistant-attach'){
      const conversation=activeConversation(),context=conversation.context,available=materials.filter(m=>!m.deleted&&context.type!=='restricted'&&(context.projectId?m.projectId===context.projectId||m.scope==='company':true));
      openDialog('选择本次对话资料',`<p>当前范围：${escape(context.label)}。选择资料后，后续问题使用所选资料。</p><div class="assistant-attachment-list">${available.map(m=>`<label><input type="checkbox" name="materials" value="${m.id}" ${context.materialIds.includes(m.id)?'checked':''}><span><strong>${escape(m.name)} ${escape(m.version)}</strong><small>${escape(sourceScope(m))} · ${sourceStatus(m)}</small></span></label>`).join('')}</div>`,'确认资料范围',values=>{context.materialIds=values.getAll('materials');context.sourceSnapshots=context.materialIds.map(id=>knowledge.snap(material(id)));if(context.sourcing)context.sourcing.members=structuredClone(context.sourceSnapshots);conversation.messages.push({role:'assistant',nature:'data',text:`后续问题的资料范围已更新为 ${context.materialIds.length} 项。此前消息保留原引用记录。`});closeProductDialog();renderAssistant()});return true;
    }
    if(action==='assistant-source'){
      const [index,citationIndex]=value.split(':').map(Number),citation=activeConversation().messages[index].citations[citationIndex];
      if(citation.taskId){task=tasks.find(t=>t.id===citation.taskId);project=projects.find(p=>p.id===task.projectId)}
      selectedMaterial=citation.id;sourceReturn={taskId:citation.taskId,chapter:citation.chapter,materialId:citation.id,version:citation.version,text:citation.fullText||citation.text,location:citation.location,label:'来自 Gru 对话引用',screen:'S09'};library.tab='content';setScreen('S05');return true;
    }
    if(action==='assistant-card'){
      const [index,cardIndex]=value.split(':').map(Number),card=activeConversation().messages[index].cards[cardIndex],context=activeConversation().context;
      if(card.action==='task'){useTask(card.taskId,'S11');return true}
      if(card.action==='material'){selectedMaterial=card.materialId;sourceReturn=null;library.tab='content';setScreen('S05');return true}
      if(card.action==='config'){modelManager.handle('api-home','');return true}
      if(card.action==='attachment'){research.handle('research-save-attachment',card.attachmentId);return true}
      if(card.action==='record-request'){const target=tasks.find(t=>t.id===card.taskId);if(target.revision!==card.revision||target.signedRevision===target.revision){showFeedback('任务版本已变化或已经签发，请重新确认目标。');return true}openDialog('确认登记任务要求',`<p>${escape(target.name)} · 文稿 v${card.revision}</p><p>${escape(card.request)}</p><label class="api-confirm"><input name="confirm" type="checkbox" required>确认作为待处理要求，不直接修改正文</label>`,'登记要求',()=>{if(target.revision!==card.revision){dialogError('正文版本已变化，请重新读取目标。');return}target.research.requests.push(card.request);research.recordEvent(target,'待处理输入',card.request);card.action='recorded-request';card.title='要求已经登记';card.description='在研究工作区的记录分区查看';closeProductDialog();renderScreen(screen)});return true}
      if(card.action==='recorded-request'){const target=tasks.find(t=>t.id===card.taskId);target.research.panel='activity';useTask(target.id,'S09');return true}
      if(card.action==='provide-revision'){
        const target=tasks.find(t=>t.id===context.taskId);if(target.revision!==context.revision||target.signedRevision===target.revision){showFeedback('文档状态已经变化，请使用当前修订版本。');return true}
        const before=target.modules[context.chapter].text,conversation=activeConversation();
        openDialog('填写拟替换正文',`<p>${escape(target.name)} · 第 ${context.chapter+1} 章 · v${target.revision}</p><details open><summary>当前正文</summary><p>${escape(before)}</p></details><label class="field">拟替换正文<textarea name="text" required rows="8"></textarea></label><p class="muted small">内容由你填写，提交后进入比较预览。</p>`,'进入修改预览',values=>{conversation.messages.push({role:'assistant',nature:'proposal',text:'核对拟替换内容后，可以确认写入对应章节。',proposal:{taskId:target.id,chapter:context.chapter,revision:target.revision,before,after:values.get('text')},applied:false});closeProductDialog();renderAssistant()});return true;
      }
    }
    if(action==='assistant-apply'){
      const message=activeConversation().messages[Number(value)],proposal=message.proposal,target=tasks.find(t=>t.id===proposal.taskId);
      if(message.applied||target.revision!==proposal.revision||target.modules[proposal.chapter].text!==proposal.before||target.signedRevision===target.revision){showFeedback('正文版本或签发状态已变化，请重新比较当前内容。');return true}
      openDialog('确认写入文档',`<p>${escape(target.name)} · 第 ${proposal.chapter+1} 章 · 文档 v${proposal.revision}</p><p>本次替换当前章节正文，创建修订状态，并清除旧来源核验与审查确认。</p><label><input type="checkbox" name="confirm" required> 已核对拟替换内容及影响</label>`,'写入章节',()=>{
        if(target.revision!==proposal.revision||target.modules[proposal.chapter].text!==proposal.before||target.signedRevision===target.revision){dialogError('正文已经变化，请关闭后重新检查。');return}
        task=target;project=projects.find(p=>p.id===task.projectId);chapter=proposal.chapter;invalidation('对话中确认人工修订');task.modules[chapter].text=proposal.after;message.applied=true;closeProductDialog();setScreen('S09');renderAssistant();
      });return true;
    }
    return false;
  }
  Object.assign(renderers,{S09:()=>renderDocument(false),S11:()=>renderDocument(true),S04:renderLibrary,S05:renderMaterialDetail});
  const evaluationSamples=tasks.map(t=>({id:t.id,taskId:t.id,name:t.name,projectId:t.projectId,type:t.type,revision:t.revision,texts:t.modules.map(m=>m.text),date:'2026-09-24',knowledge:t.modules.map(m=>`${material(m.sourceId).name} ${m.sourceVersion}`)}));
  function filteredSamples(){return evaluationSamples.filter(s=>(!evalFilters.category||projects.find(p=>p.id===s.projectId).category===evalFilters.category)&&s.date>=evalFilters.from&&s.date<=evalFilters.to)}
  function filteredEvents(){return events.filter(e=>{const p=e.taskId?projects.find(p=>p.id===tasks.find(t=>t.id===e.taskId)?.projectId):null;return (!evalFilters.category||p?.category===evalFilters.category)&&e.date>=evalFilters.from&&e.date<=evalFilters.to})}
  function filteredTasks(){return tasks.filter(t=>(!evalFilters.category||projects.find(p=>p.id===t.projectId).category===evalFilters.category)&&(t.created||'2026-09-24')>=evalFilters.from&&(t.created||'2026-09-24')<=evalFilters.to)}
  const gradeKey=(version,sample)=>`${version}:${sample.id}:${sample.revision}`;
  function stats(version,samples){const rated=samples.map(s=>grades[gradeKey(version,s)]).filter(Boolean);return {total:samples.length,rated:rated.length,accepted:rated.filter(g=>g.decision==='采纳').length,returned:rated.filter(g=>g.decision==='退回').length,mean:rated.length?Math.round(rated.reduce((a,g)=>a+g.score,0)/rated.length):null}}
  function configChecklist(config){const evaluated=stats(config.id,evaluationSamples);return [['默认模板已核准',material(config.template).status==='approved'],['检查规则已核准',material(config.rule).status==='approved'],['模型连接已验证并启用',modelManager.usable(config.model)],['配置字段自检',config.checked],['评测样本已逐项复核',evaluated.rated===evaluated.total&&evaluated.total>0],...capabilities.check(config)]}
  function renderAgents(){
    const header=title('Agent 管理',agentArea==='agents'?button('新建 Agent','new-agent','',true):'')+`<nav class="agent-area-tabs" aria-label="Agent 管理分区">${[['agents','Agent 配置'],['models','模型 API 配置']].map(([id,label])=>`<button type="button" data-action="agent-area" data-value="${id}" aria-pressed="${agentArea===id}">${label}</button>`).join('')}</nav>`;
    if(agentArea==='models')return header+modelManager.render();
    const config=versions.find(v=>v.id===configId),editable=config.status==='草稿',check=configChecklist(config);
    let body=capabilities.render(config,configTab);
    if(configTab==='defaults')body=`<form class="form" data-form="agent-config"><fieldset ${editable?'':'disabled'} style="border:0;padding:0;margin:0"><div class="form two"><label class="field">Agent 名称<input name="name" required value="${escape(config.name)}"></label><label class="field">默认模板<select name="template">${options(materials.filter(m=>m.category==='templates').map(m=>[m.id,`${m.name} ${m.version} · ${m.status==='approved'?'已核准':'待核准'}`]),config.template)}</select></label><label class="field">检查规则<select name="rule">${options(materials.filter(m=>m.category==='rules').map(m=>[m.id,`${m.name} ${m.version}`]),config.rule)}</select></label><label class="field">默认审查人员<select name="reviewer">${options([['林知远','林知远 · 注册审查'],['沈知微','沈知微 · 知识审核']],config.reviewer)}</select></label><label class="field wide">版本说明<textarea name="note" required>${escape(config.note)}</textarea></label></div></fieldset><div class="actions">${editable?'<button class="btn primary" type="submit">保存默认配置</button>':button('新建修订版本','new-version')}</div></form><div class="config-policy"><div><strong>创建任务</strong><p>按项目类别和文档类型选择适用模板，缺少输入时显示补充入口。</p></div><div><strong>准备正文</strong><p>采用已核准资料，各章节保存引用版本与原文位置。</p></div><div><strong>人工决定</strong><p>正文修订后重新核验；关键问题和批注处理后确认版本。</p></div></div><h3>模板与规则的当前状态</h3>${[material(config.template),material(config.rule)].map(m=>`<div class="requirement-row"><strong>${escape(m.name)} ${escape(m.version)}</strong>${button('查看资料','material-open',m.id)}</div>`).join('')}`;
    if(configTab==='model')body=`<div class="api-binding"><div><strong>模型 API 连接</strong><p>${modelManager.selectionHint()}</p></div>${button('管理 API 配置','api-home')}</div><form class="form" data-form="agent-model"><fieldset ${editable?'':'disabled'} style="border:0;padding:0"><div class="form two"><label class="field">模型配置<select name="model" required>${modelManager.selectOptions(config.model)}</select></label><label class="field">任务预算<input name="budget" type="number" min="1" max="1000" step="1" value="${config.budget}" required><span class="muted small">演示单位 / 单个文档任务</span></label><label class="field wide">适用器械类别<select name="categories">${options([['第二类,第三类','第二类与第三类'],['第二类','仅第二类'],['第三类','仅第三类']],config.categories.join(','))}</select></label></div></fieldset><div class="actions">${editable?'<button type="submit" class="btn primary">保存调用配置</button>':button('新建修订版本','new-version')}</div></form><div class="alert info" style="margin-top:20px">配置自检仅检查必填字段、版本选择和预算范围。正式模型连接尚未接入。</div>${button('执行配置自检','check-config','',false,!editable)}<div class="release-checks"><div><span>字段与预算配置</span>${tag(config.checked?'自检通过':'待自检',config.checked?'ok':'warn')}</div><div><span>正式模型连接</span>${tag('尚未接入')}</div><div><span>适用企业</span><span>当前演示企业</span></div></div>`;
    if(configTab==='release')body=`<h3>发布条件</h3><div class="release-checks">${check.map(([label,pass])=>`<div><span>${label}</span>${tag(pass?'通过':'待处理',pass?'ok':'warn')}</div>`).join('')}</div><div class="actions">${button('检查配置','check-config','',false,!editable)}${button('查看版本评测','evaluate-version',config.id)}${button('发布演示版本','publish-version','',true,!editable||check.some(([,pass])=>!pass))}${button('停用此版本','disable-version','',false,config.status!=='已发布')}</div><div class="source-excerpt">发布后，新任务使用当前版本的默认配置。已有任务继续显示创建时记录的配置版本。发布与停用仅更新当前设计会话。</div><h3>已使用此版本的任务</h3>${tasks.filter(t=>t.agent===config.id).map(t=>`<div class="backlink-row"><div><strong>${escape(t.name)}</strong><p>${escape(projects.find(p=>p.id===t.projectId).product)} · 文档 v${t.revision}</p></div>${button('打开任务','task-open',t.id)}</div>`).join('')||empty('暂无关联任务','新任务创建后可以在此追溯。')}`;
    if(configTab==='calls')body=`<div class="alert info">当前没有正式模型调用记录。以下展示当前会话内的任务操作，费用保持未记录。</div>${eventTable(events.filter(e=>e.taskId&&tasks.find(t=>t.id===e.taskId)?.agent===config.id))}`;
    if(configTab==='defaults')body+=capabilities.summary(config);
    return header+`<div class="agent-config-layout"><aside class="card agent-config-nav"><h2>Agent 与配置版本</h2>${versions.map(v=>`<button class="agent-version-button ${v.id===configId?'active':''}" data-action="config-select" data-value="${v.id}"><strong>${escape(v.name)}</strong><div class="tag-row">${tag(v.id,'info')}${tag(v.status,v.status==='已发布'?'ok':v.status==='草稿'?'warn':'neutral')}</div><p>${escape(v.note)}</p></button>`).join('')}</aside><section class="card pad agent-config-body"><div class="panel-top"><div><h2>${escape(config.name)} · ${escape(config.id)}</h2><p>${config.status==='已发布'?'新任务使用的默认配置':config.status==='草稿'?'编辑配置并完成评测后发布':'保留已有任务的配置依据'}</p></div>${tag(config.status,config.status==='已发布'?'ok':'warn')}</div><div class="product-tabs" role="tablist">${[['defaults','流程与默认配置'],['roles','能力与专业角色'],['capabilities','Skill 与工具'],['model','模型与预算'],['limits','研究上限'],['release','评测与发布'],['calls','任务记录']].map(([id,name])=>`<button role="tab" aria-selected="${id===configTab}" data-action="config-tab" data-value="${id}">${name}</button>`).join('')}</div>${body}</section></div>`;
  }
  function eventTable(list){return list.length?table(['时间','操作','操作人','对象与内容'],list.map(e=>`<tr><td>${escape(e.time)}</td><td>${escape(e.action)}</td><td>${escape(e.person)}</td><td>${escape(e.detail)}</td></tr>`)):empty('当前范围没有操作记录','执行保存、核验、审查或配置操作后，在此查看关联记录。')}
  function renderEvaluation(){
    const samples=filteredSamples(),summary=stats(compareB,samples);
    let body='';
    if(evalTab==='overview')body=`<div class="eval-kpis">${[['固定样本',summary.total,'当前范围内的任务版本'],['已评分',`${summary.rated} / ${summary.total}`,'当前候选版本'],['人工采纳',`${summary.accepted} / ${summary.rated}`,'分母为已评分样本'],['平均评分',summary.mean===null?'—':summary.mean,'满分 100，人工记录']].map(([name,value,note])=>`<div><span>${name}</span><strong data-metric="${name}">${value}</strong><small>${note}</small></div>`).join('')}</div><section class="card pad"><div class="panel-top"><h2>样本与评分记录</h2>${button('比较版本','eval-tab','compare')}</div>${sampleTable(samples,compareB)}</section>`;
    if(evalTab==='compare')body=`<section class="card pad"><div class="toolbar"><label>基准版本<select data-change="compare-a">${options(versions.map(v=>[v.id,`${v.name} ${v.id}`]),compareA)}</select></label><label>候选版本<select data-change="compare-b">${options(versions.map(v=>[v.id,`${v.name} ${v.id}`]),compareB)}</select></label></div><div class="compare-grid">${[compareA,compareB].map(id=>{const s=stats(id,samples);return `<article><h3>${escape(id)}</h3><div class="facts-grid"><div><span>相同样本范围</span><strong>${s.total} 项</strong></div><div><span>已评分</span><strong>${s.rated} / ${s.total}</strong></div><div><span>平均评分</span><strong>${s.mean??'尚未评分'}</strong></div><div><span>采纳 / 已评分</span><strong>${s.accepted} / ${s.rated}</strong></div><div><span>退回</span><strong>${s.returned}</strong></div></div></article>`}).join('')}</div><div class="compare-result">${compareA===compareB?'请选择两个不同版本。':samples.length===0?'当前筛选范围没有样本。':stats(compareA,samples).rated<samples.length||stats(compareB,samples).rated<samples.length?'相同样本尚未全部评分，当前不能判断版本质量变化。':'两个版本的相同样本已完成评分。请查看各项意见与证据，再决定发布。'}</div><h3 style="margin-top:22px">逐项复核</h3>${sampleTable(samples,compareA)}${sampleTable(samples,compareB)}</section>`;
    if(evalTab==='operations'){
      const matched=filteredTasks();
      body=`<div class="eval-kpis">${[['任务数量',matched.length],['等待人员处理',matched.filter(t=>['待人工审查','待补资料','已退回'].includes(t.status)).length],['演示已签发',matched.filter(t=>t.status==='已签发').length],['正式调用费用','未接入']].map(([name,value])=>`<div><span>${name}</span><strong>${value}</strong><small>当前筛选范围</small></div>`).join('')}</div><section class="card pad"><h2>任务与阻塞事项</h2>${matched.length?table(['文档与版本','状态','负责人','截止时间','批注','操作'],taskRows(matched)):empty('当前范围没有任务','调整类别与日期范围。')}</section>`;
    }
    if(evalTab==='audit')body=`<section class="card pad"><h2>当前会话操作记录</h2><p class="muted small">记录对象版本、处理人员、决定和操作时间；正式审计服务尚未接入。</p>${eventTable(filteredEvents())}</section>`;
    return title('评测与运营',button('导出当前范围','export-evaluation'))+`<div class="product-tabs" role="tablist">${[['overview','质量评测'],['compare','版本比较'],['operations','任务运营'],['audit','审计记录']].map(([id,name])=>`<button role="tab" aria-selected="${evalTab===id}" data-action="eval-tab" data-value="${id}">${name}</button>`).join('')}</div><section class="card pad"><form class="toolbar" data-form="evaluation-filter"><label>器械类别<select name="category">${options([['','全部类别'],['第二类','第二类'],['第三类','第三类']],evalFilters.category)}</select></label><label>开始日期<input name="from" type="date" value="${evalFilters.from}" required></label><label>结束日期<input name="to" type="date" value="${evalFilters.to}" required></label><button class="btn primary" type="submit">更新范围</button>${button('重置','eval-reset')}</form></section>${body}`;
  }
  function sampleTable(samples,version){return samples.length?table(['任务与版本',`评分 · ${escape(version)}`,'处理结果','评分人','明细'],samples.map(s=>{const g=grades[gradeKey(version,s)];return `<tr data-sample="${s.id}"><td><strong>${escape(s.name)}</strong><small>文档 v${s.revision} · 样本 ${escape(s.date)}</small></td><td>${g?`${g.score} / 100`:'待评分'}</td><td>${g?tag(g.decision,g.decision==='采纳'?'ok':'warn'):'尚未记录'}</td><td>${g?escape(g.person):'—'}</td><td>${button(g?'复核记录':'查看并评分','grade-sample',`${version}:${s.id}`)}</td></tr>`})):empty('当前筛选条件没有样本','统计数量同步为零；调整类别或日期继续查看。')}
  function gradeSample(value){
    const [version,id]=value.split(':'),sample=evaluationSamples.find(s=>s.id===id),old=grades[gradeKey(version,sample)];
    openDialog(`${sample.name} · ${version} 评分`,`<p><strong>固定文档 v${sample.revision}</strong> · ${escape(projects.find(p=>p.id===sample.projectId).name)}</p><details><summary>查看样本文本与知识版本</summary><div class="source-excerpt">${sample.texts.map(escape).join('<br><br>')}</div><p class="muted small">${sample.knowledge.map(escape).join('；')}</p></details><div class="form two"><label class="field">综合评分（0–100）<input type="number" name="score" min="0" max="100" required value="${old?.score??''}"></label><label class="field">人工决定<select name="decision">${options([['采纳','采纳'],['退回','退回']],old?.decision||'退回')}</select></label><label class="field wide">评分依据<textarea name="reason" required placeholder="说明来源支持、严格字段与目录完整性">${escape(old?.reason||'')}</textarea></label></div>${old?`<p class="muted small">上次评分：${escape(old.person)} · ${escape(old.time)} · ${old.score} 分</p>`:''}`,'保存评分',values=>{
      const key=gradeKey(version,sample),history=old?[...(old.history||[]),{score:old.score,reason:old.reason,time:old.time}]:[];
      grades[key]={score:Number(values.get('score')),decision:values.get('decision'),reason:values.get('reason'),output:values.get('output'),person:'林知远',time:clockText(),history};
      log('记录版本评分',sample.taskId,`${version} · 文档 v${sample.revision} · ${values.get('score')} 分`);closeProductDialog();renderScreen(screen);
    });
    const outputField=document.createElement('label');outputField.className='field wide';outputField.textContent='本版本评测输出';
    const output=document.createElement('textarea');output.name='output';output.required=true;output.rows=5;output.value=old?.output||(version==='D-1.4'?sample.texts.join('\n\n'):'');output.placeholder='记录此配置版本产生的输出，核对后填写评分。';outputField.append(output);dialog.querySelector('.form').prepend(outputField);
  }
  Object.assign(renderers,{S13:renderAgents,S15:renderEvaluation});
  function downloadFile(name,content){const url=URL.createObjectURL(new Blob([content],{type:'text/plain;charset=utf-8'}));const anchor=document.createElement('a');anchor.href=url;anchor.download=name;document.body.append(anchor);anchor.click();anchor.remove();setTimeout(()=>URL.revokeObjectURL(url),1000)}
  function openTaskHistory(){openDialog('文档版本与审查记录',`${task.history.map(h=>`<article class="history-entry"><strong>文档 v${h.revision} · ${escape(h.action)}</strong><p>${escape(h.time)}</p><details><summary>查看此版本正文</summary>${h.texts.map((text,index)=>`<h4>${index+1}. ${escape(task.modules[index]?.name||'正文')}</h4><p>${escape(text)}</p>`).join('')}</details></article>`).join('')}${eventTable(events.filter(e=>e.taskId===task.id))}`,'完成查看')}
  function checkConfig(){const config=versions.find(v=>v.id===configId);if(config.status!=='草稿'){showFeedback('已发布配置为只读，请创建修订版本。');return}config.checked=Boolean(config.model&&config.name&&config.budget>0&&config.budget<=1000&&config.template&&config.rule);log('配置字段自检','',`${config.name} ${config.id}`);renderScreen('S13');showFeedback('配置字段检查完成；正式模型连接尚未接入。')}
  function newVersion(name){const base=versions.find(v=>v.id===configId);const config={...base,id:`D-1.${versions.length+4}`,name:name||base.name,status:'草稿',checked:false,published:'',note:'待评测的修订配置',categories:[...base.categories],capabilities:base.capabilities?structuredClone(base.capabilities):undefined};versions.push(config);configId=config.id;configTab='defaults';log('创建配置草稿','',`${config.name} ${config.id}`);renderScreen('S13')}
  function addComment(moduleIndex,quote,start,anchor={kind:'text'}){
    if(task.signedRevision===task.revision){showFeedback('请创建修订版本后添加批注。');return}
    openDialog('添加审查批注',`<p>第 ${moduleIndex+1} 章 · ${escape(task.modules[moduleIndex].name)}</p><div class="source-excerpt">${escape(quote)}</div><label class="field">处理意见<textarea name="comment" required></textarea></label>`,'添加批注',values=>{
      task.comments.push({module:moduleIndex,quote,start,...anchor,revision:task.revision,text:values.get('comment'),person:task.publicReference?'当前审查者（会话）':'林知远',time:clockText(),resolved:false,reply:''});DocumentReader.state.filter='open';task.reviewRevision=null;task.signedRevision=null;task.status='待人工审查';log('添加审查批注',task.id,`第 ${moduleIndex+1} 章`);closeProductDialog();renderScreen(screen);
    });
  }
  function returnTask(evidence){
    if(task.signedRevision===task.revision){showFeedback('当前签发版本为只读，请创建修订版本。');return}
    openDialog(evidence?'请求补充证据':'退回文档修改',`<p>${escape(task.name)} · 文档 v${task.revision}</p><div class="form"><label class="field">目标章节<select name="chapter">${options(task.modules.map((m,index)=>[index,`${index+1}. ${m.name}`]),chapter)}</select></label><label class="field">接收人<select name="owner">${options([['周予安','周予安 · 研发支持'],['沈知微','沈知微 · 知识审核'],['林知远','林知远 · 注册审查']],evidence?'周予安':task.owner)}</select></label><label class="field">处理原因<textarea name="reason" required></textarea></label></div>`,evidence?'提交补证请求':'退回并转交',values=>{
      task.status=evidence?'待补资料':'已退回';task.owner=values.get('owner');task.reviewRevision=null;task.signedRevision=null;task.verified={};task.run='等待负责人处理';task.returnReason=values.get('reason');log(evidence?'请求补证':'退回修改',task.id,`第 ${Number(values.get('chapter'))+1} 章 · ${task.owner} · ${task.returnReason}`);closeProductDialog();renderScreen(screen);
    });
  }
  function execute(action,value,target){
    if(finalProduct.handle(action,value))return;
    if(fields.handle(action,value))return;
    if((action==='export-document'||action==='dossier-export'||action==='kw-export-new')&&(tasks.find(t=>t.id===value)||task).publicReference){fields.handle('fields-output',(tasks.find(t=>t.id===value)||task).id);return}
    if(dossier.handle(action,value))return;
    if(action==='kw-artifact'&&tasks.find(t=>t.id===value)?.modules.some(m=>m.refs?.length>1||m.refs?.some(r=>r.image))){knowledge.handle(action,value);document.querySelector('#product-dialog [data-action="kw-export-new"]').textContent='导出完整图文 HTML';return}
    if(action==='kw-export-new'&&tasks.find(t=>t.id===value)?.modules.some(m=>m.refs?.length>1||m.refs?.some(r=>r.image))){closeProductDialog();dossier.handle('dossier-export',value);return}
    if(research.handle(action,value))return;
    if(knowledge.handle(action,value))return;
    if(capabilities.handle(action,value,versions.find(v=>v.id===configId)))return;
    if(modelManager.handle(action,value))return;
    if(action==='agent-area'){agentArea=value;renderScreen('S13');return}
    if(handleAssistantAction(action,value)||handleLibraryAction(action,value))return;
    if(action==='dialog-close'){closeProductDialog();return}
    if(action==='navigate'){if(value==='S07'){taskParent=screen==='S06'?'S06':'S18';setupTaskId=null}if(value==='S06'&&screen==='S18')taskFilter='';setScreen(value);return}
    if(action==='task-step'){if(value==='S07')setupTaskId=task.id;setScreen(value);return}
    if(action==='project-open'){project=projects.find(p=>p.id===value);selectedDemoProject=project.row;setScreen('S18');return}
    if(action==='task-open'){const found=tasks.find(t=>t.id===value);useTask(value,['待人工审查','待签发','已签发','已退回'].includes(found.status)?'S11':'S09');return}
    if(action==='queue-tab'){queueTab=value;queueFilter='';renderScreen('S01');return}
    if(action==='queue-filter'){queueTab=['running','signing'].includes(value)?'all':value;queueFilter=value==='running'?'运行中':value==='signing'?'待签发':'';renderScreen('S01');return}
    if(action==='project-materials'){knowledge.reset();library={...library,scope:'project',category:'all',query:'',status:'',page:1};libraryState.view='all';libraryState.folder='';resetLibraryView();setScreen('S04');return}
    if(action==='project-collections'){setScreen('S04');knowledge.handle('kw-area','collections');return}
    if(action==='project-tasks'){taskFilter=value||'';setScreen('S06');return}
    if(action==='task-template'){
      const choices=materials.filter(m=>m.category==='templates'&&!m.deleted&&m.status==='approved'&&(m.scope==='company'||m.projectId===task.projectId));
      if(!choices.length){openDialog('选择文档模板','<p>资料中心尚无已核准的适用模板。</p>','关闭');return}
      openDialog('选择文档模板',`<label class="field">模板版本<select name="template" required>${options([['','请选择模板'],...choices.map(m=>[m.id,`${m.name} ${m.version}`])],task.template?.id||'')}</select></label><label class="field">适用依据<textarea name="reason" required placeholder="填写该模板适用于当前文档的依据"></textarea></label><label><input type="checkbox" name="applicable" required> 已确认模板适用于${escape(task.name)}</label>`,'确认模板',values=>{
        const selected=material(values.get('template')),reason=values.get('reason').trim();if(!reason){dialogError('请填写模板适用依据。');return}
        if(selected.status!=='approved'||selected.deleted)throw new Error('模板版本尚未核准');
        invalidation('确认文档模板');task.template={id:selected.id,version:selected.version,reason,person:task.owner,time:clockText()};task.templatePending=false;log('确认文档模板',task.id,reason);closeProductDialog();renderScreen(screen);
      });return;
    }
    if(action==='project-info'){openDialog('项目基本信息',`<div class="facts-grid">${[['项目',project.name],['产品',project.product],['器械类别',project.category],['注册路径',project.path],['负责人',project.owner]].map(([name,value])=>`<div><span>${name}</span><strong>${escape(value)}</strong></div>`).join('')}</div>`,'完成查看');return}
    if(action==='planned-agent'){openDialog(`${value} Agent`,`${tag('规划中')}<p>当前场景尚无已发布配置。用途、输入输出和审查规则需要完成确认，发布后才可创建任务。</p>`,'完成查看');return}
    if(action==='choose-project'){openDialog('选择项目',`<label class="field">获授权项目<select name="project">${options(projects.map(p=>[p.id,p.name]),project.id)}</select></label>`,'切换项目',values=>{project=projects.find(p=>p.id===values.get('project'));library.scope='project';resetLibraryView();closeProductDialog();renderScreen('S04')});return}
    if(action==='config-current'){const config=requirementsForSetup().config;configId=config?config.id:'D-1.5';configTab='defaults';agentArea='agents';setScreen('S13');return}
    if(action==='create-task'){
      const {config,template}=requirementsForSetup(),docs=research.selected(project.id),error=document.getElementById('setup-error');
      const blank=!['requirements','index'].includes(newType);
      if(!config||(!blank&&(!template||template.status!=='approved'))||!config.categories.includes(project.category)||['paused','archived'].includes(project.status)){
        error.className='alert error';error.textContent=!config?'当前没有可用的已发布 Agent 版本。':!template||template.status!=='approved'?'目标文档模板尚未核准，请核准适用版本。':!config.categories.includes(project.category)?'当前配置不适用于此器械类别。':'当前项目暂停或归档，无法创建新任务。';return;
      }
      task=createTask(project,newType,blank||!docs.length?'待补资料':'待人工审查',config.reviewer,blank);task.agent=config.id;task.note=newNote;task.template=template?{id:template.id,version:template.version}:null;task.templatePending=!template||template.status!=='approved';task.created=new Date().toLocaleDateString('sv-SE');setupTaskId=task.id;
      task.inputScope=research.scope(project.id);
      if(blank){task.run='等待正文与资料'}
      else if(!docs.length){task.modules=[];task.history[0].texts=[];task.run='等待项目资料'}
      else task.modules.forEach((m,index)=>{const source=docs[Math.min(index,docs.length-1)];Object.assign(m,{sourceId:source.id,sourceVersion:source.version,sourceText:source.text,location:source.location});});
      log('创建文档任务',task.id,`${project.product} · ${task.name} · ${config.id}`);chapter=0;history.replaceState(navigationState(),'',location.href);setScreen('S09');return;
    }
    if(action==='reader-filter'){DocumentReader.state.filter=value;renderScreen(screen);return}
    if(action==='reader-comment'){DocumentReader.state.filter='all';renderScreen(screen);const target=document.getElementById(`annotation-${value}`);target.scrollIntoView({block:'center'});target.focus({preventScroll:true});return}
    if(action==='reader-locate'){const c=task.comments[Number(value)],index=c.kind==='image'?task.modules[c.module].figures.findIndex(f=>f.image===c.image):-1,target=c.kind==='image'?document.getElementById(`document-figure-${c.module}-${index}`):document.querySelector(`[data-document-module="${c.module}"]`);if(!target)throw new Error('标注内容位置不存在');target.scrollIntoView({block:'center'});if(c.kind==='image'){const image=target.querySelector('.figure-select');image?.focus({preventScroll:true});if(image&&!image.disabled)selectFigure(c.module,index,image)}else{const mark=target.querySelector(`[data-value="${value}"]`);if(mark)mark.focus({preventScroll:true})}return}
    if(action==='select-figure'){const [moduleIndex,index]=value.split(':').map(Number);selectFigure(moduleIndex,index,target);return}
    if(action==='comment-image'){const anchor=selectedFigure;if(!anchor)throw new Error('请选择需要标注的图片');const figure=task.modules[anchor.module].figures[anchor.index];clearSelection();addComment(anchor.module,figure.caption,undefined,{kind:'image',image:figure.image,figureIndex:anchor.index});return}
    if(action==='chapter'||action==='chapter-source'){chapter=Number(value);if(action==='chapter-source')DocumentReader.state.folds.add(`${task.id}:source`);renderScreen(screen);document.getElementById(action==='chapter'?`task-section-${chapter}`:'source-inspector').scrollIntoView({block:'start',behavior:'smooth'});return}
    if(action==='source-original'){const m=task.modules[chapter];selectedMaterial=m.sourceId;sourceReturn={taskId:task.id,chapter,materialId:m.sourceId,version:m.sourceVersion,text:m.sourceText,location:m.location,screen};library.tab='content';setScreen('S05');return}
    if(action==='source-return'){const location=sourceReturn;sourceReturn=null;if(location.taskId){useTask(location.taskId,location.screen,location.chapter);document.getElementById(`task-section-${location.chapter}`).scrollIntoView({block:'start'})}else setScreen(location.screen);return}
    if(action==='material-open'){selectedMaterial=value;const item=material(value);sourceReturn=screen==='S07'?{materialId:value,version:item.version,text:item.text,location:item.location,screen:'S07',label:'文档模板'}:null;library.tab='content';setScreen('S05');return}
    if(action==='approve-source'){approveMaterial(task.modules[chapter].sourceId);return}
    if(action==='approve-material'){approveMaterial(value);return}
    if(action==='verify-source'){
      const m=task.modules[chapter],source=material(m.sourceId);
      if((m.refs.length>1||m.refs.some(r=>r.image))&&dossier.problems({modules:[m]}).length){showFeedback('请逐项核对本章全部文字证据和图表引用。');return}
      if(task.dirty||source.status!=='approved'||source.version!==m.sourceVersion||!document.getElementById('evidence-confirm').checked){showFeedback('请保存正文、核准资料、核对最新版本，并勾选原文核验。');return}
      m.refs.forEach(r=>r.checked=true);task.verified[chapter]=task.revision;log('核验章节来源',task.id,`第 ${chapter+1} 章 · 文档 v${task.revision} · ${source.name} ${m.sourceVersion}`);renderScreen(screen);return;
    }
    if(action==='update-source'){
      if(task.signedRevision===task.revision)throw new Error('签发版本需要创建修订后更新引用');
      const m=task.modules[chapter],source=material(m.sourceId);
      openDialog('检查资料更新影响',`<p>${escape(source.name)}：${escape(m.sourceVersion)} → ${escape(source.version)}</p><div class="compare-grid"><article><h3>当前引用</h3><p>${escape(m.sourceText)}</p></article><article><h3>新版本</h3><p>${escape(source.text)}</p></article></div><p>更新引用后，正文进入修订版本，来源和审查需要重新确认。</p>`,'更新当前任务引用',()=>{
        invalidation('更新引用版本');Object.assign(m,{sourceVersion:source.version,sourceText:source.text,location:source.location});if(m.refs)Object.assign(m.refs[0],{version:source.version,text:source.text,location:source.location,checked:false});closeProductDialog();renderScreen(screen);
      });return;
    }
    if(action==='save-document'){saveTask();return}
    if(action==='document-history'){openTaskHistory();return}
    if(action==='confirm-review'){
      if(blockers().length){showFeedback('当前版本仍有待处理事项。');return}
      task.reviewRevision=task.revision;task.status='待签发';log('确认总稿审查',task.id,`文档 v${task.revision}`);renderScreen('S11');return;
    }
    if(action==='sign-document'){
      if(blockers().length||task.reviewRevision!==task.revision||role!=='signer'){showFeedback('当前版本尚未满足签发条件。');return}
      const revision=task.revision;
      openDialog('确认演示签发',`<p><strong>${escape(project.product)} · ${escape(task.name)}</strong></p><p>待签发版本：v${revision}。当前来源核验及审查意见均对应此版本。</p><p class="muted small">此操作记录当前会话的演示状态，不生成正式电子签名。</p><label><input name="confirm" type="checkbox" required> 已检查最终版本及签发范围</label>`,'确认签发',()=>{
        if(task.revision!==revision||blockers().length||role!=='signer'){dialogError('文档或处理条件已变化，请重新核查。');return}
        task.signedRevision=revision;task.status='已签发';task.history.unshift({revision,time:clockText(),action:'演示签发',texts:task.modules.map(m=>m.text)});log('演示签发',task.id,`文档 v${revision}`);closeProductDialog();renderScreen('S11');
      });return;
    }
    if(action==='revise-document'){invalidation('创建签发后修订');renderScreen(screen);return}
    if(action==='request-evidence'||action==='return-task'){returnTask(action==='request-evidence');return}
    if(action==='resume-task'){
      if(!task.modules.length){const docs=(task.research?.scope||task.inputScope).members.filter(knowledge.available);if(!docs.length){showFeedback('请明确选择已核准资料；空范围不会自动扩展。');return}task.modules=chapterNames(task.type).map((name,index)=>{const source=docs[Math.min(index,docs.length-1)];return {id:`chapter-${index+1}`,name,text:`${project.product} · ${name}\n请按已核准资料编写本节内容。`,sourceId:source.id,sourceVersion:source.version,sourceText:source.text,location:source.location}})}
      invalidation('提交修订');task.status='待人工审查';task.owner='林知远';task.run='等待人员核验';chapter=0;renderScreen(screen);return;
    }
    if(action==='module-comment'){const index=Number(value);addComment(index,task.modules[index].text.slice(0,80));return}
    if(action==='comment-selection'){selection.hidden=true;const prefix=selectedRange.cloneRange();prefix.selectNodeContents(document.querySelector(`[data-document-module="${selectedModule}"]`));prefix.setEnd(selectedRange.startContainer,selectedRange.startOffset);addComment(selectedModule,selectedQuote,prefix.toString().length);return}
    if(action==='replace-selection'){
      if(screen==='S11')throw new Error('审查页面仅允许标注，请返回撰写页面修改正文');
      selection.hidden=true;const range=selectedRange.cloneRange(),index=selectedModule;
      openDialog('修改选中文字',`<label class="field">替换内容<textarea name="text" required>${escape(selectedQuote)}</textarea></label><p class="muted small">应用后，当前文档与审查状态会更新为待确认。</p>`,'应用修改',values=>{
        range.deleteContents();range.insertNode(document.createTextNode(values.get('text')));task.modules[index].text=document.querySelector(`[data-document-module="${index}"]`).innerText;invalidation('选区修改');closeProductDialog();renderScreen(screen);
      });return;
    }
    if(action==='resolve-comment'){
      const comment=task.comments[Number(value)];openDialog('回复并解决批注',`<p>${escape(comment.text)}</p><label class="field">处理说明<textarea name="reply" required></textarea></label>`,'记录处理结果',values=>{comment.resolved=true;comment.reply=values.get('reply');comment.resolvedAt=clockText();task.reviewRevision=null;log('解决审查批注',task.id,comment.reply);closeProductDialog();renderScreen(screen)});return;
    }
    if(action==='export-document'){if(task.modules.some(m=>m.refs)){dossier.handle('dossier-export',task.id);return}downloadFile(`${project.product}-${task.name}-v${task.revision}.md`,`# ${task.name}\n\n项目：${project.name}\n文档版本：v${task.revision}\n审查状态：${task.status}\n设计演示，当前会话记录。\n\n${task.modules.map((m,index)=>`## ${index+1}. ${m.name}\n\n${m.text}\n\n来源：${material(m.sourceId).name} ${m.sourceVersion} · ${m.location}`).join('\n\n')}`);log('导出文稿',task.id,`文档 v${task.revision}`);return}
    if(action==='task-events'){openDialog('任务操作记录',eventTable(events.filter(e=>e.taskId===value)),'完成查看');return}
    if(action==='library-scope'||action==='library-category'){library[action==='library-scope'?'scope':'category']=value;libraryState.folder='';resetLibraryView();renderScreen('S04');return}
    if(action==='library-reset'){Object.assign(library,{query:'',status:'',page:1});Object.assign(libraryState,{tag:'',owner:'',mode:'list'});renderScreen('S04');return}
    if(action==='library-page'){library.page=Number(value);renderScreen('S04');return}
    if(action==='library-tab'){library.tab=value;renderScreen(screen);return}
    if(action==='select-material'){openMaterial(value);return}
    if(action==='backlink'){const [id,index]=value.split(':');sourceReturn=null;useTask(id,'S09',Number(index));document.getElementById(`task-section-${index}`).scrollIntoView({block:'start'});return}
    if(action==='upload-material'||action==='upload-version'){uploadDialog(action==='upload-version'?value:'');return}
    if(action==='compare-material'){
      const [id,version]=value.split(':'),item=material(id),old=item.versions.find(v=>v.version===version);
      openDialog('资料版本比较',`<div class="compare-grid"><article><h3>${escape(old.version)}</h3><p>${escape(old.text)}</p></article><article><h3>当前 ${escape(item.version)}</h3><p>${escape(item.text)}</p></article></div><p>${backlinks(id).length} 处任务引用；已有任务保留各自选择的版本。</p>`,'完成查看');return;
    }
    if(action==='import-queue'){openDialog('上传与解析队列',imports.length?imports.map(i=>`<div class="import-row"><div><strong>${escape(i.name)}</strong><p>${sourceStatus(material(i.id))} · ${escape(material(i.id).version)}</p></div>${button(material(i.id).parsed?'查看资料':'补充可读取文本',material(i.id).parsed?'import-open':'import-retry',i.id)}</div>`).join(''):empty('当前会话没有上传文件','选择上传资料后，可在此查看读取与核准状态。'),'完成查看');return}
    if(action==='import-open'){closeProductDialog();selectedMaterial=value;sourceReturn=null;library.tab='content';setScreen('S05');return}
    if(action==='import-retry'){closeProductDialog();uploadDialog(value);return}
    if(action==='config-select'){configId=value;renderScreen('S13');return}
    if(action==='config-tab'){configTab=value;renderScreen('S13');return}
    if(action==='new-version'){newVersion();return}
    if(action==='new-agent'){openDialog('新建 Agent 配置',`<div class="form"><label class="field">Agent 名称<input name="name" required></label><p class="muted small">创建后进入草稿，可设置模板、规则、模型与预算。完成评测后才可发布。</p></div>`,'创建草稿',values=>{const name=values.get('name');if(versions.some(v=>v.name===name)){dialogError('Agent 名称已存在，请创建修订版本。');return}closeProductDialog();newVersion(name)});return}
    if(action==='check-config'){checkConfig();return}
    if(action==='evaluate-version'){compareB=value;evalTab='overview';evalFilters.category='';setScreen('S15');return}
    if(action==='publish-version'){
      const config=versions.find(v=>v.id===configId);if(configChecklist(config).some(([,pass])=>!pass))return;
      openDialog('发布演示版本',`<p>${escape(config.name)} ${escape(config.id)}</p><p>发布范围：当前演示企业。新任务采用此配置，历史任务保留已绑定版本。</p><label><input type="checkbox" required name="confirm"> 已复核模板、规则、样本评分和适用范围</label>`,'确认发布',()=>{versions.filter(v=>v.name===config.name&&v.status==='已发布').forEach(v=>v.status='历史版本');config.status='已发布';config.published=clockText();log('发布配置版本','',`${config.name} ${config.id}`);closeProductDialog();renderScreen('S13')});return;
    }
    if(action==='disable-version'){const config=versions.find(v=>v.id===configId);openDialog('停用配置版本',`<p>${escape(config.name)} ${escape(config.id)} 将停止用于新任务。已有 ${tasks.filter(t=>t.agent===config.id).length} 项任务保留原配置。</p><label class="field">停用原因<textarea name="reason" required></textarea></label>`,'确认停用',values=>{config.status='已停用';log('停用配置版本','',values.get('reason'));closeProductDialog();renderScreen('S13')});return}
    if(action==='eval-tab'){evalTab=value;renderScreen('S15');return}
    if(action==='eval-reset'){evalFilters={category:'',from:'2026-09-01',to:'2026-09-30'};renderScreen('S15');return}
    if(action==='grade-sample'){gradeSample(value);return}
    if(action==='export-evaluation'){
      const samples=filteredSamples();
      const records=evalTab==='audit'?filteredEvents():evalTab==='operations'?filteredTasks():samples.map(s=>({...s,grades:(evalTab==='compare'?[compareA,compareB]:[compareB]).map(version=>({version,record:grades[gradeKey(version,s)]||null}))}));
      downloadFile('当前范围记录.json',JSON.stringify({nature:'设计演示',view:evalTab,range:evalFilters,records},null,2));return;
    }
    throw new Error(`未定义操作：${action}`);
  }
  document.addEventListener('click',event=>{const target=event.target.closest('[data-action]');if(!target)return;event.preventDefault();event.stopImmediatePropagation();if(!target.disabled)execute(target.dataset.action,target.dataset.value||'',target)},true);
  document.addEventListener('change',event=>{
    if(modelManager.change(event.target))return;
    if(event.target.matches('#assistant-composer select')){assistant.mode=event.target.value;return}
    const node=event.target;if(!node.dataset.change)return;
    const change=node.dataset.change,value=node.value;
    if(change==='library-scope'){execute('library-scope',value);return}
    if(change==='material-select'){if(node.checked)libraryState.selected.add(node.dataset.id);else libraryState.selected.delete(node.dataset.id);renderScreen('S04');return}
    if(change==='library-select-all'){libraryFiltered().slice((library.page-1)*library.size,library.page*library.size).forEach(m=>{if(node.checked)libraryState.selected.add(m.id);else libraryState.selected.delete(m.id)});renderScreen('S04');return}
    if(change==='setup-project'){project=projects.find(p=>p.id===value);renderScreen('S07')}
    else if(change==='setup-type'){newType=value;renderScreen('S07')}
    else if(change==='task-note')newNote=value;
    else if(change==='task-filter'){taskFilter=value;renderScreen('S06')}
    else if(change==='review-role'){role=value;renderScreen('S11')}
    else if(change==='library-size'){library.size=Number(value);library.page=1;renderScreen('S04')}
    else if(change==='compare-a'||change==='compare-b'){if(change==='compare-a')compareA=value;else compareB=value;renderScreen('S15')}
  });
  document.addEventListener('input',event=>{
    if(event.target.id==='assistant-prompt'){assistant.draft=event.target.value;return}
    const node=event.target.closest('[data-document-module]');if(!node)return;
    task.modules[Number(node.dataset.documentModule)].text=node.innerText;invalidation('直接修改正文');updateEditorState();
  });
  document.addEventListener('submit',event=>{
    if(event.target.id==='assistant-composer'){event.preventDefault();const values=new FormData(event.target);sendAssistant(values.get('prompt'),values.get('mode'));return}
    const form=event.target;if(!form.dataset.form)return;
    event.preventDefault();const values=new FormData(form),type=form.dataset.form;
    if(knowledge.submit(type,values))return;
    if(type==='api-search'){modelManager.search(values);return}
    if(type==='library-search'){library.query=values.get('query').trim();library.status=values.get('status');Object.assign(libraryState,{tag:values.get('tag'),owner:values.get('owner'),sort:values.get('sort')});resetLibraryView();renderScreen('S04')}
    if(type==='evaluation-filter'){
      if(values.get('from')>values.get('to')){form.querySelector('[name="to"]').setCustomValidity('结束日期不能早于开始日期');form.reportValidity();return}
      evalFilters={category:values.get('category'),from:values.get('from'),to:values.get('to')};renderScreen('S15');
    }
    if(type==='agent-config'||type==='agent-model'){
      const config=versions.find(v=>v.id===configId);if(config.status!=='草稿')throw new Error('已发布配置为只读');
      if(type==='agent-config')Object.assign(config,{name:values.get('name'),template:values.get('template'),rule:values.get('rule'),reviewer:values.get('reviewer'),note:values.get('note')});
      else {if(!modelManager.usable(values.get('model')))throw new Error('模型连接未验证或已停用');Object.assign(config,{model:values.get('model'),budget:Number(values.get('budget')),categories:values.get('categories').split(',')})}
      config.checked=false;Object.keys(grades).filter(key=>key.startsWith(`${config.id}:`)).forEach(key=>delete grades[key]);log('修改默认配置','',`${config.name} ${config.id} · 需要重新自检与评测`);renderScreen('S13');showFeedback('当前会话的配置已保存，发布前需要重新检查与评测。');
    }
  });
  function clearSelection(){selection.hidden=true;selectedFigure=null;document.querySelectorAll('.figure-select[aria-pressed="true"]').forEach(node=>node.setAttribute('aria-pressed','false'))}
  function positionSelection(rect){selection.hidden=false;const width=selection.getBoundingClientRect().width;selection.style.left=`${Math.max(8,Math.min(innerWidth-width-8,rect.left))}px`;selection.style.top=`${Math.max(8,Math.min(innerHeight-52,rect.top-48))}px`}
  function selectFigure(moduleIndex,index,node){
    clearSelection();if(screen!=='S11'||task.signedRevision===task.revision)return;
    if(!task.modules[moduleIndex].figures[index])throw new Error('所选图片不存在');
    window.getSelection().removeAllRanges();selectedFigure={module:moduleIndex,index};node.setAttribute('aria-pressed','true');
    selection.querySelectorAll('button').forEach(button=>button.hidden=button.dataset.action!=='comment-image');positionSelection(node.getBoundingClientRect());
  }
  function detectSelection(event){
    if(event.target.closest('.selection-popover,.product-dialog'))return;
    if(event.target.closest('.figure-select'))return;
    clearSelection();
    const selected=window.getSelection();if(!selected||selected.isCollapsed){selection.hidden=true;return}
    const range=selected.getRangeAt(0),start=range.startContainer.nodeType===3?range.startContainer.parentElement:range.startContainer,end=range.endContainer.nodeType===3?range.endContainer.parentElement:range.endContainer;
    const paragraph=start.closest('[data-document-module]');
    if(!paragraph||!paragraph.contains(end)||task.signedRevision===task.revision){selection.hidden=true;return}
    selection.querySelector('[data-action="replace-selection"]').hidden=screen==='S11';selection.querySelector('[data-action="ask-selection"]').hidden=screen==='S11';selection.querySelector('[data-action="comment-image"]').hidden=true;selection.querySelector('[data-action="comment-selection"]').hidden=false;
    selectedRange=range.cloneRange();selectedQuote=selected.toString();selectedModule=Number(paragraph.dataset.documentModule);
    positionSelection(range.getBoundingClientRect());
  }
  document.addEventListener('pointerdown',event=>{if(!event.target.closest('.selection-popover,.figure-select,.document-paragraph'))clearSelection()});
  document.addEventListener('scroll',()=>{if(selection.hidden)return;const anchor=selectedFigure?document.querySelector(`#document-figure-${selectedFigure.module}-${selectedFigure.index} .figure-select`):selectedRange;if(!anchor)return;const rect=anchor.getBoundingClientRect();if(rect.bottom<68||rect.top>innerHeight){clearSelection();return}positionSelection(rect)},true);
  window.addEventListener('resize',clearSelection);
  document.addEventListener('mouseup',detectSelection);
  document.addEventListener('toggle',event=>{const node=event.target;if(!node.isConnected||!node.dataset.readerFold)return;if(node.open)DocumentReader.state.folds.add(node.dataset.readerFold);else DocumentReader.state.folds.delete(node.dataset.readerFold)},true);
  document.addEventListener('keydown',event=>{if(event.target.matches('mark[data-action]')&&(event.key==='Enter'||event.key===' ')){event.preventDefault();execute(event.target.dataset.action,event.target.dataset.value)} });
  document.addEventListener('keyup',event=>{if(event.target.matches('[data-document-module]'))detectSelection(event)});
  document.addEventListener('keydown',event=>{if((event.metaKey||event.ctrlKey)&&event.key.toLowerCase()==='s'&&['S09','S11'].includes(screen)){event.preventDefault();saveTask()}});
  document.addEventListener('keydown',event=>{
    if(event.key==='Escape'){clearSelection();window.getSelection().removeAllRanges()}
    if((event.metaKey||event.ctrlKey)&&event.key.toLowerCase()==='j'){event.preventDefault();if(assistant.open)hideAssistant();else showAssistant()}
    if(event.key==='Escape'&&assistant.open&&!dialog.open){event.preventDefault();hideAssistant()}
    if(event.target.id==='assistant-prompt'&&event.key==='Enter'&&!event.shiftKey&&!event.isComposing){event.preventDefault();document.getElementById('assistant-composer').requestSubmit()}
  });
  document.addEventListener('input',event=>{if(event.target.matches('[name="to"],[name="from"]'))document.querySelector('[name="to"]')?.setCustomValidity('')});
  document.querySelector('#S03 .table').classList.add('project-list');
  if(new URLSearchParams(location.search).get('panel')==='model-api'){agentArea='models';showScreen('S13')}
  else if(new URLSearchParams(location.search).get('scenario')==='public'){const destination=document.querySelector('.screen.active').id;fields.handle('fields-public','');if(destination==='S11')setScreen('S11')}
  else if(new URLSearchParams(location.search).get('scenario')==='complex'){useTask(complexTasks[0].id,'S11')}
  else if(['S09','S11'].includes(document.querySelector('.screen.active').id)){const destination=document.querySelector('.screen.active').id;fields.handle('fields-public','');setScreen(destination)}
  else showScreen(document.querySelector('.screen.active').id);
  navigationReady=true;
})();
