

package com.impaircheck.fragment

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.mediapipe.tasks.components.containers.Category
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import com.impaircheck.databinding.FaceBlendshapesResultBinding

class FaceBlendshapesResultAdapter :
    RecyclerView.Adapter<FaceBlendshapesResultAdapter.ViewHolder>() {
    companion object {
        private const val NO_VALUE = "--"
    }

    private var categories: MutableList<Category?> = MutableList(52) { null }

    fun updateResults(faceLandmarkerResult: FaceLandmarkerResult? = null) {
        categories = MutableList(52) { null }
        if (faceLandmarkerResult != null && faceLandmarkerResult.faceBlendshapes().isPresent) {
            val faceBlendshapes = faceLandmarkerResult.faceBlendshapes().get()
            val sortedCategories = faceBlendshapes[0].sortedByDescending { it.score() }
            val min = kotlin.math.min(sortedCategories.size, categories.size)
            for (i in 0 until min) {
                categories[i] = sortedCategories[i]
            }
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {
        val binding = FaceBlendshapesResultBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        categories[position].let { category ->
            holder.bind(category?.categoryName(), category?.score())
        }
    }

    override fun getItemCount(): Int = categories.size

    inner class ViewHolder(private val binding: FaceBlendshapesResultBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(label: String?, score: Float?) {
            with(binding) {
                tvLabel.text = label ?: NO_VALUE
                tvScore.text = if (score != null) String.format(
                    "%.2f",
                    score
                ) else NO_VALUE
            }
        }
    }


}
