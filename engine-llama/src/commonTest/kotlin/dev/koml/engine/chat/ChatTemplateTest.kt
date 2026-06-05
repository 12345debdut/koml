package dev.koml.engine.chat

import dev.koml.core.model.PromptTemplate
import dev.koml.core.session.ChatMessage
import dev.koml.core.session.ChatRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChatTemplateTest {

    private val sample = listOf(
        ChatMessage(ChatRole.System, "You are concise."),
        ChatMessage(ChatRole.User, "Hi"),
    )

    @Test fun chatml_renders_correct_format() {
        val rendered = ChatMLTemplate.render(sample)
        assertEquals(
            """
            <|im_start|>system
            You are concise.<|im_end|>
            <|im_start|>user
            Hi<|im_end|>
            <|im_start|>assistant

            """.trimIndent(),
            rendered,
        )
        assertEquals(listOf("<|im_end|>"), ChatMLTemplate.defaultStopSequences)
    }

    @Test fun llama3_uses_begin_of_text_and_eot_id() {
        val rendered = Llama3Template.render(sample)
        assertTrue(rendered.startsWith("<|begin_of_text|>"))
        assertTrue(rendered.contains("<|start_header_id|>system<|end_header_id|>\n\nYou are concise.<|eot_id|>"))
        assertTrue(rendered.endsWith("<|start_header_id|>assistant<|end_header_id|>\n\n"))
        assertEquals(listOf("<|eot_id|>"), Llama3Template.defaultStopSequences)
    }

    @Test fun phi3_uses_role_tags_and_end_token() {
        val rendered = Phi3Template.render(sample)
        assertTrue(rendered.contains("<|system|>\nYou are concise.<|end|>"))
        assertTrue(rendered.contains("<|user|>\nHi<|end|>"))
        assertTrue(rendered.endsWith("<|assistant|>\n"))
    }

    @Test fun gemma_merges_system_into_first_user() {
        val rendered = GemmaTemplate.render(sample)
        // Gemma has no system role — system content prefixes the first user message.
        assertTrue(rendered.contains("<start_of_turn>user\nYou are concise.\n\nHi<end_of_turn>"))
        assertTrue(rendered.endsWith("<start_of_turn>model\n"))
        // Should NOT contain a separate system turn.
        assertTrue(!rendered.contains("<start_of_turn>system"))
    }

    @Test fun gemma_handles_user_only() {
        val rendered = GemmaTemplate.render(listOf(ChatMessage(ChatRole.User, "ping")))
        assertEquals(
            """
            <start_of_turn>user
            ping<end_of_turn>
            <start_of_turn>model

            """.trimIndent(),
            rendered,
        )
    }

    @Test fun none_concatenates_messages_with_newlines() {
        val rendered = NoneTemplate.render(sample)
        assertEquals("You are concise.\nHi", rendered)
        assertEquals(emptyList(), NoneTemplate.defaultStopSequences)
    }

    @Test fun forPromptTemplate_picks_the_right_template() {
        assertEquals(NoneTemplate, ChatTemplate.forPromptTemplate(PromptTemplate.None))
        assertEquals(ChatMLTemplate, ChatTemplate.forPromptTemplate(PromptTemplate.ChatML))
        assertEquals(Llama3Template, ChatTemplate.forPromptTemplate(PromptTemplate.Llama3))
        assertEquals(Phi3Template, ChatTemplate.forPromptTemplate(PromptTemplate.Phi3))
        assertEquals(GemmaTemplate, ChatTemplate.forPromptTemplate(PromptTemplate.Gemma))
    }
}
