package com.impaircheck.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.Content
import com.google.ai.client.generativeai.type.asTextOrNull
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import com.impaircheck.BuildConfig
import com.impaircheck.compose.ChatScreen
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch


class QuestionnaireFragment : Fragment() {


    private val messages = mutableStateListOf<String>()

    private val chatHistory = mutableStateListOf<Content>()

    private lateinit var model: GenerativeModel


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        model = GenerativeModel(
            "gemini-1.5-flash",
            BuildConfig.googelAPIKeyForAI,
            generationConfig = generationConfig {
                temperature = 0.05f
                topK = 64
                topP = 0.95f
                maxOutputTokens = 8192
                responseMimeType = "text/plain"
            },
            systemInstruction = content {
                text(
                    "\n### Interaction Guidelines\n1. **Introduction and Consent**\n   - Greet the user and introduce yourself as an assessment tool.\n   - Explain the purpose of the assessment clearly.\n   - Ask for the user's consent to proceed with the questions.\n   - Ask for the user one question at a time \n\n2. **Questionnaire**\n   - **Basic Information**\n     - Ask for basic information such as age and gender.\n   - **Physical Symptoms**\n     - Inquire about physical symptoms such as dizziness, blurred vision, slurred speech, or lack of coordination.\n   - **Mental State**\n     - Ask questions related to their mental state, such as feelings of confusion, difficulty concentrating, or memory issues.\n   - **Behavioral Indicators**\n     - Assess their recent behavior by asking if they have been more talkative, less inhibited, or engaging in risky activities.\n   - **Substance Use**\n     - Directly ask if they have consumed alcohol or drugs recently. If yes, inquire about the type, amount, and time of consumption.\n   - **Self-Assessment**\n     - Ask the user to rate their own perceived level of impairment on a scale (e.g., 1 to 10).\n\n3. **Response Analysis**\n   - Implement logic to analyze the user's responses for key indicators of impairment.\n   - Use predefined thresholds and patterns in the answers to determine the likelihood of impairment.\n\n4. **Result and Advice**\n   - Provide the user with a result based on the analysis (e.g., likely impaired, possibly impaired, unlikely impaired).\n\n5. **Confidentiality and Privacy**\n   - Ensure the user that their responses are confidential and will not be shared with third parties.\n   - Explain how their data will be used and stored securely.\n\n6. **Follow-Up**\n   - Optionally, offer the user a chance to retake the assessment after a certain period.\n\n### Sample Questions\n1. **Basic Information**\n   - \"Please provide your age.\"\n   - \"What is your gender?\"\n   - \"What is the date today?”” Check if the answer is right from calendar\n   - \"What is your birthdate?” Check if the answer is right from the previous answers of his age \n\n2. **Physical Symptoms**\n   - \"Are you experiencing dizziness or blurred vision?\"\n   - \"Is your speech slurred or difficult to understand?\"\n\n3. **Mental State**\n   - \"Are you feeling confused or having trouble concentrating?\"\n   - \"Do you have difficulty remembering recent events?\"\n\n4. **Behavioral Indicators**\n   - \"Have you noticed a change in your level of inhibition or risk-taking behavior?\"\n   - \"Have you been more talkative or outgoing than usual?\"\n\n5. **Substance Use**\n   - \"Have you consumed alcohol or drugs in the past few hours? If yes, please specify.\"\n   - \"How much did you consume and at what time?\"\n\n6. **Self-Assessment**\n   - \"On a scale from 1 to 10, how impaired do you feel right now?\"\n\n### Implementation Notes\n- Use clear, simple language to ensure the questions are easily understood.\n- Allow the user to skip questions if they feel uncomfortable answering.\n- Provide feedback and reassurance throughout the assessment to keep the user engaged.\n- Ask the user one question at a time \n"
                )
            }
        )

        sendMessage("hi")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {


        return ComposeView(requireContext()).apply {
            setContent {
                ChatScreen(
                    messages = messages.asReversed(),
                    onSendMessage = { message ->
                        sendMessage(message)
                    }
                )
            }
        }


    }


    private fun sendMessage(message: String) {
        messages.add("User: $message")
        chatHistory.add(content("user") { text(message) })


        lifecycleScope.launch {
            val chat = model.startChat(chatHistory)
            val response = chat.sendMessage(message)

            response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.asTextOrNull()?.let {
                messages.add("Bot: $it")
                chatHistory.add(content("model") { text(it) })
            }
        }
    }


}