import type { SkillDTO } from '../api/skill';

const COMPANY_PREFIXES = [
  '阿里巴巴',
  '阿里云',
  '阿里',
  '字节跳动',
  '字节',
  '腾讯',
  '美团',
  '百度',
  '京东',
  '华为',
  '小米',
  '快手',
  '网易',
  '拼多多',
  '滴滴',
  '小红书',
  '蚂蚁',
  '携程',
  'OPPO',
  'vivo',
  '荣耀',
  'B站',
  '哔哩哔哩',
];

export function getSkillDisplayName(skill?: SkillDTO | null): string {
  const rawName = skill?.name?.trim() || '';
  if (!rawName) {
    return '';
  }

  for (const prefix of COMPANY_PREFIXES) {
    if (rawName.startsWith(prefix)) {
      const stripped = rawName.slice(prefix.length).replace(/^[\s\-_|｜/·•]+/, '').trim();
      if (stripped) {
        return stripped;
      }
    }
  }

  return rawName;
}

export function getTemplateName(skillId: string, skills: SkillDTO[]): string {
  return getSkillDisplayName(skills.find(s => s.id === skillId)) || skillId;
}
