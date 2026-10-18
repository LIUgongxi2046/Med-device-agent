import { computed, onBeforeUnmount, ref, watch } from 'vue'
import useUserStore from '@/store/modules/user'
import type { MedicalSession } from './protocol'
/** One active page generation; changing identity or hiding the page wipes sensitive presentation. */
export function usePolicyPage(reset:()=>void,bootstrap:()=>Promise<void>){
 const user=useUserStore(),loading=ref(false),error=ref(''),busy=computed(()=>loading.value||user.medicalWriting)
 let controller=new AbortController(),generation=0,mounted=true,ownsWrite=false
 const reader=():MedicalSession=>{if(!user.medicalSession?.current_context)throw new Error('请先选择当前企业');return user.medicalSession}
 function current(g:number,s:MedicalSession){return mounted&&generation===g&&user.medicalSession===s&&!document.hidden}
 function clear(){controller.abort();controller=new AbortController();generation++;loading.value=false;error.value='';reset()}
 async function run(work:(signal:AbortSignal,current:()=>boolean)=>Promise<void>,write=false){const session=user.medicalSession as MedicalSession|null;if(!session?.current_context||busy.value||document.hidden)return;const g=generation;error.value='';if(write){ownsWrite=true;user.medicalWriting=true}else loading.value=true
  try{await work(controller.signal,()=>current(g,session))}catch(failure){if(current(g,session))error.value=failure instanceof Error?failure.message:'操作失败，请核查当前身份与原请求'}finally{if(g===generation)loading.value=false;if(write&&ownsWrite){ownsWrite=false;user.medicalWriting=false}}
 }
 function visibility(){if(document.hidden)clear();else if(!ownsWrite)void bootstrap()}
 // Register after callers have initialized their page state, avoiding synchronous setup access.
 function start(){watch(()=>user.medicalSession,()=>{clear();if(!ownsWrite)void bootstrap()},{immediate:true,flush:'sync'});document.addEventListener('visibilitychange',visibility)}
 onBeforeUnmount(()=>{mounted=false;clear();document.removeEventListener('visibilitychange',visibility)})
 return {user,loading,error,busy,reader,clear,run,start,writing:()=>ownsWrite}
}
