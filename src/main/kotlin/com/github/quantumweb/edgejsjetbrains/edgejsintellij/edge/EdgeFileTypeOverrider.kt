package com.github.quantumweb.edgejsjetbrains.edge

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.impl.FileTypeOverrider
import com.intellij.openapi.vfs.VirtualFile

class EdgeFileTypeOverrider : FileTypeOverrider {
    override fun getOverriddenFileType(file: VirtualFile): FileType? {
        return if (file.extension == EdgeFileType.defaultExtension) EdgeFileType else null
    }
}
