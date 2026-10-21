import { reactive } from 'vue'
import { session } from './fixtures'
const state=reactive({medicalWriting:false,medicalSession:session})
export default ()=>state
