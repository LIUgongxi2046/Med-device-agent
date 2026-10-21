// Isolated build-only transport. No fetch, XMLHttpRequest, server writes or persistence.
import { conversation, session } from './fixtures'
function blocked():never {throw Error('独立模拟预览禁止写入或访问后端')}
export default {
 async get(path:string){
  let data:unknown,revision='1'
  if(path==='/api/gru-feature-availability')data={schema_version:'1.0.0',current_context:session.current_context,features:{gru_records:true}}
  else if(path==='/api/gru-record-export-feature-availability')data={schema_version:'1.0.0',current_context:session.current_context,features:{gru_record_export:false}}
  else if(path==='/api/gru-conversations')data={schema_version:'1.0.0',project_id:conversation.project_id,mode:'RECORD_ONLY',items:[Object.fromEntries(Object.entries(conversation).filter(([key])=>!['messages','message_records','page_info'].includes(key)))]}
  else if(path==='/api/gru-conversations/'+conversation.conversation_id){data=conversation;revision=conversation.revision}
  else return blocked()
  return {status:200,headers:{'cache-control':'no-store','x-schema-version':'1.0.0','x-request-id':'00000000-0000-0000-0000-000000000099'},data:{schema_version:'1.0.0',request_id:'00000000-0000-0000-0000-000000000099',resource_revision:revision,data:JSON.parse(JSON.stringify(data))}}
 },post:blocked,put:blocked,patch:blocked,delete:blocked
}
