function createRegistrationDossier(ui,knowledge) {
  const {escape,button,tag,table,options,empty}=WorkspaceUI;
  const {materials,tasks,projects,openDialog,closeProductDialog,dialogError}=ui;
  const asset='ui-delivery/assets/evidence/';
  const groups=[
    ['监管信息',['1.1 章节目录','1.2 申请表','1.3 术语、缩写词列表','1.4 产品列表','1.5 关联文件','1.6 申报前与监管机构的联系情况和沟通记录','1.7 符合性声明']],
    ['综述资料',['2.1 章节目录','2.2 概述','2.3 产品描述','2.4 适用范围和禁忌证','2.5 申报产品上市历史','2.6 其他需说明的内容']],
    ['非临床资料',['3.1 章节目录','3.2 产品风险管理资料','3.3 医疗器械安全和性能基本原则清单','3.4 产品技术要求及检验报告','3.5 研究资料','3.6 非临床文献','3.7 稳定性研究','3.8 其他资料']],
    ['临床评价资料',['4.1 章节目录','4.2 临床评价资料','4.3 其他资料']],
    ['产品说明书和标签样稿',['5.1 章节目录','5.2 产品说明书','5.3 标签样稿','5.4 其他资料']],
    ['质量管理体系文件',['6.1 综述','6.2 章节目录','6.3 生产制造信息','6.4 质量管理体系程序','6.5 管理职责程序','6.6 资源管理程序','6.7 产品实现程序','6.8 质量管理体系的测量、分析和改进程序','6.9 其他质量体系程序信息','6.10 质量管理体系核查文件']]
  ];
  const packages=new Map();let group=0,filter='all',expanded=false,selectedCitation=null;
  const get=id=>{const item=materials.find(m=>m.id===id);if(!item)throw new Error('引用资料不存在');return item};
  const snapshot=(id,version)=>{const item=get(id),record=item.versions.find(v=>v.version===version);if(!record)throw new Error('引用资料版本不存在');return {id,version,name:item.name,text:record.text,location:record.location,status:record.status}};
  function prepare(task){task.modules.forEach((m,index)=>{if(!m.refs)m.refs=[{...snapshot(m.sourceId,m.sourceVersion),type:'text',caption:'',page:null,checked:task.verified[index]===task.revision}]})}
  function init(){
    const book={id:'evidence-book',name:'设计输入与验证证据册（演示）.pdf',version:'v1.0',status:'approved',category:'project',folderId:'project',scope:'project',projectId:projects[0].id,author:'周予安',source:'用户授权的设计演示原件',updated:'2026-09-27',parsed:true,uploaded:false,demo:true,tags:['原页截图','型号比较','验证条件'],favorite:false,deleted:false,activity:[],location:'共 3 页 · 表 1、表 2、图 1',text:'演示原件包含型号配置表、采样频率冲突记录和软件界面样稿。500 Hz 与 250 Hz 需要核对条件；图片不能替代原始验证记录。',url:asset+'design-evidence-v1.pdf',versions:[]};
    book.pages=[1,2,3].map(page=>({page,image:`${asset}design-evidence-v1-page-${page}.png`,label:['型号配置表','验证条件核查','软件界面样稿'][page-1]}));book.versions.push({version:book.version,text:book.text,status:book.status,location:book.location,url:book.url,pages:structuredClone(book.pages),updated:book.updated});materials.push(book);
    const target=tasks.find(t=>t.id==='task-5');target.name='产品描述与型号配置说明（演示）';
    const blocks=[
      ['产品概述','本项目按境内第二类非体外诊断器械首次注册进行设计演示，产品名称、管理类别、分类编码、适用范围和注册单元均需由注册人员分别确认。当前页面展示产品描述文档，其余申请资料在项目资料清单中独立维护。',['sample-13','sample-1']],
      ['结构与组成','型号 A 与型号 B 的主机、采集附件、软件和接口存在配置差异。型号 C 的组合验证及连接图尚未齐备，不能把其他型号的证据直接用于该配置。下方表格及图示均保留资料中心的固定原页。',['sample-11','sample-4']],
      ['性能指标','本节用于说明产品特征及跨资料核查问题。采样频率在设计输入与实验记录中存在不同取值，需要明确模式、配置与试验条件。正式产品技术要求应单独维护可客观判定的成品指标及对应检验方法。',['sample-2','sample-8']],
      ['检验方法','检验依据需要逐项登记指标条款、方法条款、样品配置、仪器、步骤和结果判定。原始验证记录缺少条件或签字页时，保留待补证状态。截图提供原文定位，完整报告继续作为证据文件保存。',['sample-18','sample-3']],
      ['软件与运行模式','软件发布版本、完整版本命名规则、适用硬件配置、核心功能和运行环境分别记录。图示仅证明所展示的界面状态，不能替代软件验证、网络安全或算法性能研究资料。',['sample-7','sample-1']],
      ['型号比较与适用限制','型号比较应涵盖结构组成、功能、技术参数、附件、运行模式和适用条件。任何不一致都需要关联原始证据和决定记录，资料缺项不能通过复制其他型号结论补充。',['sample-1','sample-4']],
      ['包装运输与版本影响','运输验证与包装方案分别保存版本。现有记录引用包装 v1.0，资料中心已有 v2.0；当前文稿保留历史依据，并登记变更是否影响运输结论的人工核查。',['sample-15','sample-16']],
      ['适用范围、禁忌证与未完成事项','预期用途、使用环境、目标用户、适用人群和禁忌证需跨申请表、综述、说明书与临床评价进行一致性核查。当前缺少正式声明与临床路径决定，相关申请项保持待确认。',['sample-5','sample-6']]
    ];
    target.modules=blocks.map(([name,text,ids],index)=>{const source=get(ids[0]);return {id:`chapter-${index+1}`,name,text,sourceId:source.id,sourceVersion:source.version,sourceText:source.text,location:source.location,refs:ids.map(id=>({...snapshot(id,get(id).version),type:'text',caption:'',page:null,checked:false}))}});
    [1,2,4].forEach((chapter,index)=>target.modules[chapter].refs.push({...snapshot(book.id,book.version),type:index===0?'table':'image',page:index+1,image:book.pages[index].image,caption:book.pages[index].label,checked:false}));
    target.modules[6].refs[1]={...snapshot('sample-16','v1.0'),type:'text',caption:'运输记录关联的历史包装版本',page:null,checked:false};
    target.history[0].texts=target.modules.map(m=>m.text);target.comments[0].quote='采样频率';
    target.history[0].modules=structuredClone(target.modules);
    target.research.scope.members.push(snapshot(book.id,book.version));
  }
  function dossier(projectId){
    if(!packages.has(projectId)){
      const rows=groups.flatMap(([name,names],g)=>names.map((title,index)=>({id:`${g}-${index}`,group:g,title,owner:['林知远','周予安','沈知微'][g%3],applicability:'待确认',reason:'',bindings:[],reviewed:false})));
      if(projects.find(p=>p.id===projectId).publicReference)rows.forEach(row=>row.owner='待指定');
      if(projectId===projects[0].id){const row=rows.find(r=>r.title==='2.3 产品描述');row.bindings=[snapshot('evidence-book','v1.0'),snapshot('sample-1','v2.1')];row.applicability='适用';row.reason='演示项目包含多个型号，需要说明差异。'}
      packages.set(projectId,rows);
    }
    const rows=packages.get(projectId);
    rows.forEach(row=>{if(row.bindings.some(b=>get(b.id).deleted||get(b.id).status!=='approved'||get(b.id).version!==b.version))row.reviewed=false});
    return rows;
  }
  function banner(projectId){const rows=dossier(projectId);return `<section class="dossier-banner"><div><strong>申请资料清单</strong><p>首次注册通用目录 · 六类资料 · ${rows.length} 个核查项 · ${rows.filter(r=>r.applicability==='待确认').length} 项适用性待确认</p><small>按 2021 年第 121 号公告组织；具体产品、路径与适用性需人工确认。</small></div>${button(expanded?'收起资料清单':'查看申请资料清单','dossier-toggle',projectId,true)}</section>${expanded?renderPackage(projectId):''}`}
  function renderPackage(projectId){const rows=dossier(projectId),visible=rows.filter(r=>r.group===group&&(filter==='all'||filter==='missing'&&!r.bindings.length||filter==='unconfirmed'&&r.applicability==='待确认'));return `<section class="card pad dossier-package"><div class="panel-top"><h2>申请资料准备与适用性</h2><div class="actions">${button('全部','dossier-filter',`${projectId}:all`)}${button('缺少文件','dossier-filter',`${projectId}:missing`)}${button('适用性待确认','dossier-filter',`${projectId}:unconfirmed`)}</div></div><div class="dossier-layout"><nav aria-label="申请资料分类">${groups.map(([name],i)=>`<button class="${group===i?'active':''}" data-action="dossier-group" data-value="${projectId}:${i}">${name}<small>${rows.filter(r=>r.group===i).length} 项</small></button>`).join('')}</nav><div>${visible.length?table(['申请项','适用性与资料','责任人','操作'],visible.map(r=>`<tr><td><strong>${escape(r.title)}</strong><small>${escape(r.reason||'尚未登记决定依据')}</small></td><td>${tag(r.applicability,r.applicability==='待确认'?'warn':'info')}<small>${r.bindings.length} 份固定资料 · ${r.reviewed?'已核查':'待核查'}</small></td><td>${escape(r.owner)}</td><td>${button('关联与核查','dossier-item',`${projectId}:${r.id}`)}</td></tr>`)):empty('当前条件没有项目','切换分类或查询条件。')}</div></div><p class="muted">清单核查与正式签章分别处理。变更、延续、进口或体外诊断项目需要采用各自适用要求，不能直接提交此演示清单。</p></section>`}
  function citations(task,index){const m=task.modules[index];if(!m.refs)return '';return `<section class="document-evidence" aria-label="${escape(m.name)}引用证据"><div class="panel-top"><h4>本节证据与图表</h4>${button('从资料中心插入引用','dossier-insert',`${task.id}:${index}`,false,task.signedRevision===task.revision)}</div>${m.refs.map((r,j)=>`<article class="evidence-block"><div class="tag-row">${tag(r.type==='image'?'原页截图':r.type==='table'?'表格原页':'文字依据','info')}${tag(r.checked?'已核对':'待核对',r.checked?'ok':'warn')}</div>${r.image?`<figure><button class="evidence-image-button" data-action="dossier-citation" data-value="${task.id}:${index}:${j}"><img src="${escape(r.image)}" alt="${escape(r.caption)}，来自${escape(r.name)}第${r.page}页" loading="lazy"></button><figcaption>${escape(r.caption)} · ${escape(r.name)} ${escape(r.version)} · 第 ${r.page} 页</figcaption></figure>`:`<p>${escape(r.text)}</p>`}<div class="citation-controls">${button(`${r.name} ${r.version}${r.page?` · 第 ${r.page} 页`:''}`,'dossier-citation',`${task.id}:${index}:${j}`)}${button('编辑引用','dossier-edit',`${task.id}:${index}:${j}`,false,task.signedRevision===task.revision)}</div></article>`).join('')}</section>`}
  function inspect(task){if(!selectedCitation||selectedCitation.taskId!==task.id)return '';const {chapter,index}=selectedCitation,r=task.modules[chapter].refs[index],item=get(r.id);return `<section class="card pad citation-inspector"><div class="panel-top"><h2>证据原页</h2>${button('关闭预览','dossier-close')}</div><strong>${escape(r.name)} ${escape(r.version)}</strong><p>${r.page?`第 ${r.page} 页 · `:''}${escape(r.location)}</p>${r.image?`<a href="${escape(r.image)}" target="_blank" rel="noopener"><img src="${escape(r.image)}" alt="${escape(r.caption)}完整原页"></a>`:`<div class="source-excerpt">${escape(r.text)}</div>`}<p>${escape(r.caption||'固定版本原文')}</p>${item.version!==r.version?'<div class="alert">资料存在新版本，当前引用保留历史内容。</div>':''}<div class="actions">${button('打开资料中心原件','dossier-original',`${task.id}:${chapter}:${index}`)}${button('确认此项引用','dossier-confirm',`${task.id}:${chapter}:${index}`,true,task.signedRevision===task.revision)}</div></section>`}
  function materialPages(item){const record=item.versions?item.versions.find(v=>v.version===item.version):item;if(!record.pages?.length)return '';return `<section class="material-pages"><h3>原件页面与图表</h3><p>${record.publicOriginal?'官方原件转换预览；页码为转换后的阅读位置。原始 Word 文件单独保存。':'演示 PDF 与原页截图使用同一固定来源。'}</p>${record.pages.map(p=>`<details><summary>第 ${p.page} 页 · ${escape(p.label)}</summary><img src="${escape(p.image)}" alt="${escape(p.label)}原页"><a href="${escape(record.url)}#page=${p.page}" target="_blank" rel="noopener">打开 PDF 第 ${p.page} 页</a></details>`).join('')}</section>`}
  function problems(task){return task.modules.flatMap(m=>(m.refs||[]).filter(r=>{const item=get(r.id);return !r.checked||item.deleted||item.status!=='approved'||item.version!==r.version}).map(r=>`${m.name}：${r.name} ${r.version} 引用待核查`))}
  function markdown(task){return task.modules.map(m=>`## ${m.name}\n\n${m.text}\n\n${(m.refs||[]).map(r=>`${r.image?`![${r.caption}](${r.image})\n\n`:''}引用：${r.name} ${r.version} · ${r.page?`第 ${r.page} 页 · `:''}${r.location} · ${r.checked?'已核对':'待核对'}\n${r.image?`图注：${r.caption}`:r.text}`).join('\n\n')}`).join('\n\n')}
  function documentHtml(task){return `<!doctype html><html lang="zh-CN"><meta charset="utf-8"><title>${escape(task.name)}</title><style>body{max-width:880px;margin:40px auto;padding:24px;color:#243247;font:16px/1.8 system-ui}figure{margin:24px 0}img{max-width:100%}aside{border-left:3px solid #b6c6d8;padding:12px;margin:16px 0}small,figcaption{color:#465970}section{margin:40px 0}</style><h1>${escape(task.name)}</h1><p>设计演示 · 不可用于注册申报 · 文稿 v${task.revision} · ${escape(task.status)}</p>${task.modules.map(m=>`<section><h2>${escape(m.name)}</h2><p>${escape(m.text)}</p>${(m.refs||[]).map(r=>{if(r.image&&!window.EvidenceImages[r.image])throw new Error('导出图片资源不存在');return `<aside>${r.image?`<figure><img src="${window.EvidenceImages[r.image]}" alt="${escape(r.caption)}"><figcaption>${escape(r.caption)}</figcaption></figure>`:`<p>${escape(r.text)}</p>`}<small>引用：${escape(r.name)} · ${escape(r.version)} · ${r.page?`第 ${r.page} 页 · `:''}${escape(r.location)} · ${r.checked?'已核对':'待核对'}</small></aside>`}).join('')}</section>`).join('')}</html>`}
  function handle(action,value){
    if(!action.startsWith('dossier-'))return false;
    if(action==='dossier-export'){const target=tasks.find(t=>t.id===value);if(target.dirty){ui.notify('请保存正文后导出图文稿。');return true}knowledge.exportFile(target,`${target.name}-v${target.revision}.html`,documentHtml(target));return true}
    if(action==='dossier-toggle'){expanded=!expanded;ui.refresh();return true}
    if(action==='dossier-close'){selectedCitation=null;ui.refresh();return true}
    if(action==='dossier-group'||action==='dossier-filter'){const [,key]=value.split(':');if(action==='dossier-group')group=Number(key);else filter=key;ui.refresh();return true}
    if(action==='dossier-item'){
      const [projectId,id]=value.split(':'),row=dossier(projectId).find(r=>r.id===id);if(!row)throw new Error('申请项不存在');
      if(['paused','archived'].includes(projects.find(p=>p.id===projectId).status)){ui.notify('项目暂停或归档，申请资料清单只读。');return true}
      const files=materials.filter(m=>!m.deleted&&m.status!=='disabled'&&(m.projectId===projectId||m.scope==='company'));
      openDialog(row.title,`<div class="form"><label class="field">适用性<select name="applicability">${options(['待确认','适用','不适用'].map(x=>[x,x]),row.applicability)}</select></label><label class="field">决定依据与适用范围<textarea name="reason" required>${escape(row.reason)}</textarea></label><label class="field">责任人<input name="owner" required value="${escape(row.owner)}"></label><fieldset class="choice-list"><legend>关联资料中心固定版本</legend>${files.map(m=>`<label><input type="checkbox" name="files" value="${m.id}" ${row.bindings.some(b=>b.id===m.id)?'checked':''}>${escape(m.name)} ${escape(row.bindings.find(b=>b.id===m.id)?.version||m.version)}</label>`).join('')}</fieldset><p class="muted">已有引用保留固定版本；资料更新后需明确选择采用最新版本。</p><label><input type="checkbox" name="latest">将所选资料更新为最新版本</label><label><input type="checkbox" name="reviewed" ${row.reviewed?'checked':''}>已核对决定依据及所关联资料</label></div>`,'保存申请项',values=>{
        const state=values.get('applicability'),ids=values.getAll('files'),reason=values.get('reason').trim(),owner=values.get('owner').trim();
        const bindings=ids.map(id=>!values.has('latest')&&row.bindings.some(b=>b.id===id)?row.bindings.find(b=>b.id===id):snapshot(id,get(id).version));
        if(!reason||!owner){dialogError('请填写决定依据与责任人。');return}
        if(values.has('reviewed')&&(state==='待确认'||state==='适用'&&(!ids.length||bindings.some(b=>get(b.id).status!=='approved'||get(b.id).version!==b.version)))){dialogError('确认适用性并关联已核准资料后才能完成核查。');return}
        Object.assign(row,{applicability:state,reason,owner,bindings,reviewed:values.has('reviewed')});closeProductDialog();ui.refresh();
      });return true;
    }
    const [taskId,chapterText,indexText]=value.split(':'),task=tasks.find(t=>t.id===taskId),chapter=Number(chapterText),index=Number(indexText);if(!task)throw new Error('引用任务不存在');const m=task.modules[chapter];
    if(action==='dossier-citation'){selectedCitation={taskId,chapter,index};ui.refresh();return true}
    if(action==='dossier-original'){const r=m.refs[index];ui.openOriginal(task,chapter,r);return true}
    if(task.signedRevision===task.revision){ui.notify('已签发版本只读，请创建修订版本。');return true}
    if(action==='dossier-confirm'){const r=m.refs[index],item=get(r.id);if(task.dirty||item.status!=='approved'||item.version!==r.version||item.deleted){ui.notify('请保存正文并确认当前引用资料已核准、版本有效。');return true}r.checked=true;ui.event(task,'引用核验',`${m.name} · ${r.name} ${r.version}`);ui.refresh();return true}
    if(action==='dossier-insert'||action==='dossier-edit'){
      const current=action==='dossier-edit'?m.refs[index]:null,choices=materials.filter(x=>!x.deleted&&x.status!=='disabled'&&(x.projectId===task.projectId||x.scope==='company'));
      if(!choices.length){openDialog('从资料中心插入引用','<p>当前项目没有可引用资料，请在资料中心上传文件。</p>','关闭');return true}
      openDialog(current?'编辑文档引用':'从资料中心插入引用',`<div class="form"><label class="field">资料文件<select name="file" required>${options(choices.map(x=>[x.id,`${x.name} · ${x.version}`]),current?.id||choices[0].id)}</select></label><label class="field">引用类型<select name="type">${options([['text','文字依据'],['table','表格原页'],['image','原页截图']],current?.type||'text')}</select></label><label class="field">原页页码（图表必填）<input type="number" name="page" min="1" step="1" value="${current?.page||''}"></label><label class="field">图注或引用说明<textarea name="caption">${escape(current?.caption||'')}</textarea></label><p class="muted">同一资料默认保留引用版本。采用新版本需要明确勾选；图表必须已有对应原页。</p><label><input type="checkbox" name="latest">采用所选资料最新版本</label>${current&&index>0?button('移除此项引用','dossier-remove',value):''}</div>`,'保存引用',values=>{
        const item=get(values.get('file')),version=current?.id===item.id&&!values.has('latest')?current.version:item.version,record=item.versions.find(v=>v.version===version);
        const type=values.get('type'),page=Number(values.get('page')),caption=values.get('caption').trim(),original=record.pages?.find(p=>p.page===page);
        if(type!=='text'&&(!original||!caption)){dialogError('请填写图注，并选择该资料已存在的原页页码。');return}
        const ref={...snapshot(item.id,version),type,page:type==='text'?null:page,image:type==='text'?null:original.image,caption,checked:false};
        ui.invalidate(task,'调整文档引用');if(current)m.refs[index]=ref;else m.refs.push(ref);
        if(current&&index===0||m.refs.length===1)Object.assign(m,{sourceId:ref.id,sourceVersion:ref.version,sourceText:ref.text,location:ref.location});
        selectedCitation={taskId,chapter,index:current?index:m.refs.length-1};closeProductDialog();ui.refresh();
      });return true;
    }
    if(action==='dossier-remove'){if(index===0)throw new Error('主要来源需要通过编辑引用修改');closeProductDialog();openDialog('移除文档引用',`<p>${escape(m.refs[index].name)} ${escape(m.refs[index].version)}</p><p>移除后当前文稿需要重新核验，资料中心保留原件。</p>`,'确认移除',()=>{ui.invalidate(task,'移除文档引用');m.refs.splice(index,1);selectedCitation=null;closeProductDialog();ui.refresh()});return true}
    throw new Error('引用操作不存在');
  }
  init();return {banner,citations,inspect,materialPages,problems,markdown,documentHtml,handle,prepare,references:id=>[...packages.values()].flat().filter(row=>row.bindings.some(b=>b.id===id)).length};
}
