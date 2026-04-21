package interview.guide.modules.interview.service;

import interview.guide.modules.interview.model.PersonaType;
import org.springframework.stereotype.Service;

/**
 * 面试官人格策略服务。
 * 统一处理岗位风格与 strict/friendly 风格叠加。
 */
@Service
public class InterviewerPersonaService {

    private static final String DEFAULT_BASE_PERSONA = "使用专业、直接、可执行的技术面试官风格。";

    private static final String STRICT_STYLE = """
        【人格风格：STRICT】
        1. 追问直接，不做冗长铺垫。
        2. 对结论必须要求证据、边界和权衡。
        3. 对模糊回答要继续追问，直到给出可验证细节。
        """;

    private static final String FRIENDLY_STYLE = """
        【人格风格：FRIENDLY】
        1. 语气友好但保持技术深度。
        2. 先简短确认候选人观点，再引导其补全关键细节。
        3. 对不完整回答允许一次提示后继续深入追问。
        """;

    public PersonaType resolvePersonaType(String rawPersonaType) {
        return PersonaType.fromNullable(rawPersonaType);
    }

    public String normalizePersonaType(String rawPersonaType) {
        return resolvePersonaType(rawPersonaType).name();
    }

    public String buildPersonaSection(String basePersona, String rawPersonaType) {
        String effectiveBasePersona = (basePersona == null || basePersona.isBlank())
            ? DEFAULT_BASE_PERSONA
            : basePersona;

        PersonaType personaType = resolvePersonaType(rawPersonaType);
        String styleSection = personaType == PersonaType.FRIENDLY ? FRIENDLY_STYLE : STRICT_STYLE;

        return effectiveBasePersona + "\n\n" + styleSection;
    }
}
