function createDocumentFields(ui){
  const {escape,button,tag,table,options}=WorkspaceUI;
  const {materials,tasks,projects,openDialog,closeProductDialog,dialogError}=ui;
  const definitions={
    ptr:['产品技术要求',[
      ['编号与名称','技术要求编号|中文产品名称|申请阶段','拟申请编号允许留空'],
      ['型号规格','型号|规格|划分依据|结构配置差异|功能差异|关键尺寸或参数|图表说明','全部申报型号'],
      ['软件信息','发布版本|完整版本命名规则|对应型号|对应组件','含软件时'],
      ['性能指标','指标编号|指标名称|适用型号|可判定要求|单位|范围或容差|条件|标准编号及年代号','每项指标'],
      ['检验方法','方法编号|对应指标|适用型号|采用方法及标准|试验步骤|计算方法或结果表述|原理|样品制备与保存|设备|环境条件','每项指标的方法覆盖'],
      ['IVD 方法','参考品或标准品|样本制备|试验次数|计算方法','IVD 产品'],
      ['术语','术语名称|定义|使用位置','特殊术语适用时'],
      ['附录','附录编号|标题|关联正文|适用内容|图表及来源','按产品要求']]],
    overview:['综述资料',[
      ['产品概述','通用名称及依据|管理类别|分类目录及编码|适用范围|申请背景','必须核查'],
      ['产品描述','工作原理|组成材料|关键组件|软件|接口及联用关系|交付状态|图示','按有源无源属性'],
      ['型号对照','型号规格|组成配置|功能|运行模式|技术参数|差异说明','每个申报型号'],
      ['包装说明','包装级别|包装材料|无菌屏障及完整性|图示','各级包装'],
      ['研发背景','研发目的|前代或同类产品|技术比较|差异依据','必须核查'],
      ['适用范围','诊疗功能|疾病或状态|使用阶段|目标人群|使用部位|用户技能|使用环境|一次或重复使用|联合使用条件','跨文件共享'],
      ['禁忌与警示','禁忌证|限制|适用人群条件|注意事项','未知不等于无'],
      ['上市历史','国家或地区|上市日期|销售期间与数量|不良事件|召回原因|影响和措施','保留期间及口径'],
      ['配合使用','产品或附件名称|型号|接口|注册证明|相容性依据','联合使用时']]],
    ifu:['说明书',[
      ['产品标识','产品名称|型号|规格','必须'],['注册与服务主体','注册人或备案人|住所|联系方式|售后服务单位|进口代理人','依主体属性'],
      ['生产主体','生产企业|住所|生产地址|联系方式|生产许可或备案编号|受托企业','依生产关系'],
      ['注册证明','注册证或备案编号|证明原件','拟申请样稿留待取得'],['技术要求编号','产品技术要求编号|对应文档版本','拟申请样稿留待取得'],
      ['性能组成用途','产品性能|主要结构组成或成分|适用范围','必须'],['禁忌与警示','禁忌证|注意事项|使用对象|安全危害及限制|事故处理|监测评估控制|包装破损|联合使用|相互干扰|不良事件成分|废弃处理','依产品条件'],
      ['安装与使用','安装步骤|使用步骤|操作图示|个人使用安全说明','必须'],['维护与储运','保养方法|储存条件|运输条件|处理方法','依产品条件'],
      ['日期与期限','生产日期表达|使用期限或失效日期|期限依据','不生成批次日期'],['更换与复用','配件更换周期|耗材更换方法|清洗|消毒|包装|灭菌|重复使用次数或限制','依产品条件'],
      ['符号解释','图形|符号|缩写|解释','与标签一致'],['修订日期','编制或修订日期|文档版本','真实版本记录'],['其他标注','适用规定|要求内容','按专用要求']]],
    label:['标签样稿',[
      ['标识与主体','产品名称型号规格|注册人备案人及代理|注册或备案编号|生产企业|生产许可或备案编号','依产品属性'],
      ['期限与使用条件','生产日期|期限或失效日期|电源连接条件|输入功率|储存操作条件','依产品属性'],
      ['符号与警示','图形符号|警示注意事项|环境危害警示|辐射警示|最小销售单元样稿','依产品属性']]],
    principles:['基本原则清单',[
      ['原则条款','条款号|要求|适用|证明符合性采用的方法|为符合性提供客观证据的文件','保留官方五列']]],
    risk:['风险管理',[
      ['风险与控制','风险标识|生命周期范围|依据|危害及情形|可预见事件过程|损害|风险分析评价|控制措施|实施证据|有效性证据|剩余风险|总体剩余风险与受益|说明书警示|责任人员','逐项风险记录']]],
    research:['研究报告',[
      ['研究设计','研究标识版本|目的范围|依据及接受准则|样本型号批次|代表性|方案|设备条件|执行方法时间','通用结构设计'],
      ['结果与附件','原始数据及附件|计算或统计方法|结果|执行与方案差异|处理|结论局限|责任人员|签署日期','真实研究未执行时保持待补证'],
      ['适用研究','材料化学物理|电气机械环境EMC辐射|软件网络安全|生物学及生物源|清洁消毒灭菌|动物必要性|稳定性包装运输|非临床文献','每项条件分别确认']]],
    clinical:['临床评价',[
      ['封面及描述','产品名称|型号规格|人员签名原件|完成时间|产品描述研发背景','真实签署'],
      ['评价范围与路径','评价范围|相关组件|排除内容及依据|评价路径|选择理由|已有证据|待补证据','不预设免于评价'],
      ['产品对比','比较项目|申报产品|对比产品|相同或差异|支持资料|差异安全有效性论证','重复对比表'],
      ['数据与文献','来源|合法获取依据|检索方案报告|纳入排除|重复资料处理|全文|相关性贡献|分析方法及理由|安全性能结果及限制','重复数据表'],
      ['临床试验','设计依据|机构|时间|评价指标|入选排除|样本量|随访|结果|伦理原件|方案|知情同意样本|报告|数据库|分析说明及代码','依真实路径'],
      ['免于评价对比','核准目录版本条目|境内已注册对比产品|对比项目|目录器械|申报产品|差异|支持资料|分析研究','免于评价路径专用'],
      ['结论','基本原则符合性|宣称证据|安全临床性能|受益风险|上市后研究|未解决限制','缺证据禁止确认结论'],
      ['评价人员与其他','资质|经验|职责|证明原件|其他附件','真实人员记录']]],
    qms:['质量体系',[
      ['生产制造','场所|工艺|关键控制点|委托关系|原材料采购件来源|质量控制','按实际生产'],
      ['文件与职责资源','文件控制程序|记录控制|管理职责|人员资质培训|基础设施|工作环境','真实程序记录'],
      ['设计开发','输入|输出|评审|验证|确认|设计转换|变更|实际记录','产品实现子节点'],
      ['采购','供应商评价|采购要求|验证|实际记录','产品实现子节点'],
      ['生产服务','清洁污染控制|安装|过程确认|标识追溯|实际记录','产品实现子节点'],
      ['监视测量设备','设备目录|校准状态|控制程序|实际记录','产品实现子节点'],
      ['测量分析改进','符合性监视|分析|改进|其他体系信息','真实程序记录'],
      ['核查文件','企业基本情况|组织图|平面及区域图|净化检测报告|工艺图|设备目录|自查报告|既往产品条件对比','按生产条件']]],
    change:['变更资料',[
      ['变更事项','原注册证|历次变更|变更前|变更后|原因|涉及型号|风险影响|验证证据|临床影响|说明书标签影响|质量体系影响','路径由注册人员确认']]],
    renewal:['延续资料',[
      ['延续检查','原注册证|历次变更|有效期限|适用标准更新|继续研究要求|完成情况证据|监管沟通|声明','独立延续模板']]]
  };
  let current='ptr',expanded=false,publicId=null;
  const get=id=>{const item=materials.find(m=>m.id===id);if(!item)throw new Error('字段来源不存在');return item};
  function state(task){if(!task.fieldState)task.fieldState={schemas:{},attributes:{kind:'待确认',origin:'待确认',application:'待确认',authority:'',channel:'',software:'待确认',sterile:'待确认',contact:'待确认'},history:[]};return task.fieldState}
  function rows(task,type=current){const s=state(task);if(!s.schemas[type])s.schemas[type]=definitions[type][1].map((d,i)=>({id:`${type}-${i}`,group:i,values:{},status:'未填写',sources:[],reason:'',person:'',time:''}));return s.schemas[type]}
  function missing(task){return Object.keys(definitions).flatMap(type=>rows(task,type).filter(r=>!['已确认','不适用'].includes(r.status)).map(r=>`${definitions[type][1][r.group][0]}：${r.status}`))}
  function panel(task){
    const s=state(task),list=rows(task),actions=r=>button('填写与核查','fields-edit',`${task.id}:${current}:${r.id}`)+button('增加记录','fields-add',`${task.id}:${current}:${r.group}`);
    const status=r=>`${tag(r.status,r.status==='已确认'?'ok':'warn')}<small>${r.sources.length} 份固定证据</small>${r.person?`<small>${escape(r.person)} · ${escape(r.time)}</small>`:''}`;
    const headers=current==='principles'?['条款号','要求','适用','证明符合性采用的方法','为符合性提供客观证据的文件','审查与操作']:['字段组与条件','已填写内容','状态与证据','操作'];
    const content=current==='principles'?list.map(r=>`<tr>${headers.slice(0,5).map(key=>`<td>${escape(r.values[key]||'待补证')}</td>`).join('')}<td>${status(r)}${actions(r)}</td></tr>`):list.map(r=>`<tr><td><strong>${escape(definitions[current][1][r.group][0])}</strong><small>${escape(definitions[current][1][r.group][2])}</small></td><td>${Object.entries(r.values).filter(([,v])=>v).map(([k,v])=>`<p><b>${escape(k)}</b>：${escape(v)}</p>`).join('')||'<span class="muted">等待真实资料</span>'}</td><td>${status(r)}</td><td>${actions(r)}</td></tr>`);
    return `<section class="card pad structured-fields"><div class="panel-top"><div><h2>字段与证据核对</h2><p>目录、字段、原件与人工确认分别记录 · ${task.publicReference?'公开参考资料，项目适用性未核准':'客户产品字段不预填'}</p></div>${button(expanded?'收起字段':'展开字段','fields-toggle')}</div>${expanded?`<div class="actions">${button('产品属性与申请路径','fields-attributes',task.id)}${button('跨文件一致性','fields-consistency',task.id)}${button('正文与审查材料','fields-output',task.id)}</div><div class="field-type-tabs">${Object.entries(definitions).map(([key,[name]])=>button(name,'fields-type',key,key===current)).join('')}</div><div class="alert info">${definitions[current][0]} · ${list.length} 组记录 · ${list.filter(r=>r.status==='已确认').length} 组已确认 · 字段版本 ${s.revision||0} · 属性：${escape(s.attributes.kind)} / ${escape(s.attributes.application)}</div>${table(headers,content)}<p class="muted">保留必需字段。说明书 14 组、技术要求 8 组；多型号、指标方法、临床对比和风险记录支持重复行。不适用须保存依据、人员与时间。</p>`:''}</section>`;
  }
  function saveImpact(task,reason,before,after){task.reviewRevision=null;task.verified={};const s=state(task);s.revision=(s.revision||0)+1;s.history.push({time:new Date().toISOString(),reason,fieldRevision:s.revision,documentRevision:task.revision,before,after});ui.refresh()}
  function saveAttributes(task,values){const s=state(task),before=structuredClone(s.attributes),affected=[];for(const key of Object.keys(s.attributes))s.attributes[key]=values.get(key);for(const [type,list] of Object.entries(s.schemas))for(const row of list)if(['已确认','不适用'].includes(row.status)){affected.push({type,id:row.id,status:row.status});row.status='待审查'}closeProductDialog();saveImpact(task,'更新产品属性，重新核查条件字段',{attributes:before,affected},{attributes:structuredClone(s.attributes),affected:affected.map(r=>({...r,status:'待审查'}))})}
  function handle(action,value){
    if(action==='fields-public'){if(!publicId)publicId=ui.installPublic();ui.openTask(publicId);expanded=true;current='overview';ui.refresh();return true}
    if(action==='fields-toggle'){expanded=!expanded;ui.refresh();return true}
    if(action==='fields-type'){current=value;ui.refresh();return true}
    if(!action.startsWith('fields-'))return false;
    const [id,type,rowId]=value.split(':'),task=tasks.find(t=>t.id===id);if(!task)throw new Error('字段任务不存在');const s=state(task);
    if(action==='fields-output'){
      openDialog('正文与独立审查材料',`<p>${escape(task.name)} · 文稿 v${task.revision}</p><p>正式正文需要核准路径、模板和格式；当前服务不提供可直接申报的 DOCX/PDF。</p><div class="actions">${button('下载独立审查记录 JSON','fields-download',id)}${button('检查正式正文导出条件','fields-formal',id)}</div>`,'完成查看');return true;
    }
    if(action==='fields-download'){ui.download(`${task.name}-v${task.revision}-审查材料.json`,JSON.stringify({document:task.name,documentRevision:task.revision,nature:task.publicReference?'公开参考资料，非申报文件':'待审资料',fields:s,issues:missing(task),sources:task.modules.flatMap(m=>m.refs||[]),exportedAt:new Date().toISOString()},null,2));closeProductDialog();return true}
    if(action==='fields-formal'){dialogError('正式导出未执行：路径、项目适用模板、真实字段与渠道格式需核准；DOCX/PDF 排版服务尚未接入。');return true}
    if(task.signedRevision===task.revision){ui.notify('已签发版本只读，请创建修订版本。');return true}
    if(action==='fields-attributes'){
      openDialog('产品属性与申请路径',`<div class="form">${[['kind','器械属性',['待确认','非 IVD','IVD']],['origin','境内或进口',['待确认','境内','进口','外部参考']],['application','申请类型',['待确认','首次注册','延续注册','变更注册','变更备案','公开资料研究']],['software','含软件',['待确认','是','否']],['sterile','无菌属性',['待确认','无菌','非无菌']],['contact','人体接触',['待确认','接触','不接触']]].map(([key,label,values])=>`<label class="field">${label}<select name="${key}">${options(values.map(v=>[v,v]),s.attributes[key])}</select></label>`).join('')}<label class="field">受理机关<input name="authority" value="${escape(s.attributes.authority)}"></label><label class="field">申报渠道及版本<input name="channel" value="${escape(s.attributes.channel)}"></label><p class="muted">未知属性保留待确认。首次非 IVD 模板仅适用于已确认的相应路径。保存属性后，已确认和不适用字段转为待审查。</p></div>`,'保存属性',v=>saveAttributes(task,v));return true;
    }
    if(action==='fields-consistency'){
      const names=['产品名称','型号','发布版本','适用范围','禁忌证','使用期限或失效日期'],comparisons=names.map(name=>{const locations=Object.entries(s.schemas).flatMap(([key,items])=>items.filter(r=>r.values[name]).map(r=>({document:definitions[key][0],value:r.values[name],status:r.status})));return {name,locations,conflict:new Set(locations.map(x=>x.value)).size>1}});
      openDialog('共享字段与跨文件检查',table(['共享字段','各文档原值','检查结果'],comparisons.map(c=>`<tr><td>${c.name}</td><td>${c.locations.map(l=>`${escape(l.document)}：${escape(l.value)}`).join('<br>')||'尚无真实字段'}</td><td>${c.conflict?'存在差异，需核对条件与来源':c.locations.length>1?'原值一致，待人工核查':'缺少可比较文档'}</td></tr>`))+'<p>相同取值不代表已完成专业核验。修改字段会取消当前任务的审查确认。</p>','完成查看');return true;
    }
    const list=rows(task,type);
    if(action==='fields-add'){list.push({id:`${type}-row-${list.length}`,group:Number(rowId),values:{},status:'未填写',sources:[],reason:'',person:'',time:''});saveImpact(task,'增加字段记录');return true}
    if(action==='fields-edit'){
      const row=list.find(r=>r.id===rowId),definition=definitions[type][1][row.group],labels=definition[1].split('|'),available=materials.filter(m=>!m.deleted&&m.status!=='disabled'&&(m.scope==='company'||m.projectId===task.projectId));
      openDialog(definition[0],`<div class="form field-editor">${labels.map((label,index)=>`<label class="field">${escape(label)}<textarea name="value-${index}" placeholder="从原始资料填写；未知保持空白">${escape(row.values[label]||'')}</textarea></label>`).join('')}<label class="field">填写状态<select name="status">${options(['未填写','待补证','存在冲突','待审查','已确认','不适用'].map(x=>[x,x]),row.status)}</select></label><label class="field">决定依据与适用条件<textarea name="reason">${escape(row.reason)}</textarea></label><label class="field">确认人员<input name="person" value="${escape(row.person)}"></label><fieldset class="choice-list"><legend>原始证据固定版本</legend>${available.map(m=>`<label><input type="checkbox" name="sources" value="${m.id}" ${row.sources.some(r=>r.id===m.id)?'checked':''}>${escape(m.name)} ${escape(m.version)} ${m.demo?'· 设计演示':''}</label>`).join('')}</fieldset></div>`,'保存字段',values=>{
        const payload=Object.fromEntries(labels.map((label,index)=>[label,values.get(`value-${index}`).trim()])),status=values.get('status'),ids=values.getAll('sources'),reason=values.get('reason').trim(),person=values.get('person').trim();
        if(['已确认','不适用'].includes(status)&&(!reason||!person||!ids.length||ids.some(id=>get(id).status!=='approved'||get(id).demo))){dialogError('确认需要真实已核准证据、决定依据与确认人员；演示资料不能证明真实产品字段。');return}
        if(['已确认','不适用'].includes(status)&&(['kind','origin','application','software','sterile','contact'].some(key=>s.attributes[key]==='待确认')||!s.attributes.authority||!s.attributes.channel)){dialogError('产品属性、受理机关与渠道版本尚未确认，无法确认条件字段。');return}
        if(status==='已确认'&&labels.some(label=>!payload[label]&&!(type==='ptr'&&label==='技术要求编号'&&payload['申请阶段']==='拟申请'))){dialogError('尚有未填写字段；条件不适用请保留独立说明和证据。');return}
        if(status==='已确认'&&type==='ptr'&&row.group===3&&!list.some(r=>r.group===4&&r.values['对应指标']===payload['指标编号']&&r.values['适用型号']===payload['适用型号'])){dialogError('指标缺少对应检验方法或型号覆盖，不能确认。');return}
        const before=structuredClone(row);Object.assign(row,{values:payload,status,sources:ids.map(id=>({id,version:get(id).version,location:get(id).location})),reason,person,time:new Date().toISOString()});closeProductDialog();saveImpact(task,'字段修改，重新核查关联文档',before,structuredClone(row));
      });return true;
    }
    throw new Error('字段操作不存在');
  }
  return {panel,handle,state,rows,missing,definitions};
}
