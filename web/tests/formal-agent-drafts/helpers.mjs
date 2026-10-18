import {pathToFileURL} from 'node:url'
const module=await import(process.env.MEDICAL_REPO?pathToFileURL(process.env.MEDICAL_REPO+'/web/tests/formal-capability/helpers.mjs'):new URL('../formal-capability/helpers.mjs',import.meta.url))
export const {loadModule,setupVue,session,id,response,plain,storage,tick,webRoot}=module
