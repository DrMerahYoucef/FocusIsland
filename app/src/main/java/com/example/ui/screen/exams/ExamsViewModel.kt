package com.example.ui.screen.exams

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.FocusFlowApplication
import com.example.data.db.entity.ExamEntity
import com.example.widget.WidgetRefreshScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ExamsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = FocusFlowApplication.instance.examRepository

    val exams: StateFlow<List<ExamEntity>> = repository.getAllExams()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Last deleted exam, kept so the user can undo. */
    private var lastDeleted: ExamEntity? = null

    /** Creates a new exam, or updates it when [id] is an existing exam id. */
    fun saveExam(id: Long?, name: String, subject: String, examDateEpoch: Long, color: Int) {
        viewModelScope.launch {
            repository.insert(
                ExamEntity(id = id ?: 0L, name = name.trim(), subject = subject.trim(), examDate = examDateEpoch, color = color)
            )
            WidgetRefreshScheduler.refreshNow(getApplication())
        }
    }

    /** Kept for existing callers. */
    fun addExam(name: String, subject: String, examDateEpoch: Long, color: Int) =
        saveExam(null, name, subject, examDateEpoch, color)

    fun deleteExam(exam: ExamEntity) {
        viewModelScope.launch {
            lastDeleted = exam
            repository.delete(exam)
            WidgetRefreshScheduler.refreshNow(getApplication())
        }
    }

    fun undoDelete() {
        val exam = lastDeleted ?: return
        lastDeleted = null
        viewModelScope.launch {
            repository.insert(exam)
            WidgetRefreshScheduler.refreshNow(getApplication())
        }
    }
}
