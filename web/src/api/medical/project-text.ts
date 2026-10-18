export function projectTextIssue(value: string, maximum: number): string {
  if (value.trim() !== value || value.length === 0 || Array.from(value).length > maximum || value.normalize('NFC') !== value
      || /[\u0000-\u001f\u007f-\u009f]/u.test(value)) return '请填写规范文本，保留完整原值并检查长度、空白及控制字符'
  return ''
}

