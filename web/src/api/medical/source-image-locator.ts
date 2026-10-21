import Ajv2020 from 'ajv/dist/2020.js'
import schema from '../../../../business/medical-persistence/src/main/resources/source-image-locator.schema.json'
import { fail, validate } from './protocol'

export interface SourceImageLocator {
  schema_version: '1.0.0'; kind: 'IMAGE'; frame_index: number; original_sha256: string
  width: number; height: number; x0: number; y0: number; x1: number; y1: number
  coordinate_system: 'ORIGINAL_TOP_LEFT_NORMALIZED'
}
const image = new Ajv2020({ strict: true }).compile(schema)
export function validateSourceLocator(value: Record<string, unknown>): void {
  if (value.kind !== 'IMAGE') {
    validate('https://schemas.medical-agent.invalid/1.0.0/data-design/dataset.schema.json#/$defs/locator', value)
    return
  }
  if (!image(value) || Number(value.x0) >= Number(value.x1) || Number(value.y0) >= Number(value.y1)) {
    fail('LOCATOR_INVALID', '图片原件定位无效')
  }
}
