package com.fireflyapp.lite.ui.template

import androidx.fragment.app.Fragment
import com.fireflyapp.lite.data.model.TemplateType

object TemplateFactory {
    fun create(templateType: TemplateType): Fragment {
        return TemplateCatalog.create(templateType)
    }
}
