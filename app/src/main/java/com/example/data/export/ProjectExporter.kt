package com.example.data.export

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.domain.model.Project
import com.example.domain.model.ProjectFile
import com.example.domain.model.ProjectPathValidator
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ProjectExporter {

    fun exportProjectZip(context: Context, project: Project): File? {
        return try {
            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val cleanName = project.name.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val zipFile = File(exportDir, "${cleanName}.zip")

            ZipOutputStream(FileOutputStream(zipFile)).use { zipOut ->
                project.files.forEach { file ->
                    val safePath = ProjectPathValidator.normalizeRelativePath(file.path)
                        ?: throw IllegalArgumentException("Chemin de fichier non autorisé dans le projet")
                    val entry = ZipEntry(safePath)
                    zipOut.putNextEntry(entry)
                    zipOut.write(file.content.toByteArray(Charsets.UTF_8))
                    zipOut.closeEntry()
                }
            }
            zipFile
        } catch (e: Exception) {
            null
        }
    }

    fun shareProject(context: Context, project: Project) {
        val zipFile = exportProjectZip(context, project)
        if (zipFile == null) {
            Toast.makeText(context, "Erreur lors de la création de l'archive ZIP", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, zipFile)

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Export Nuru Studio : ${project.name}")
                putExtra(Intent.EXTRA_TEXT, "Voici l'archive complète du projet ${project.name} généré par Nuru Studio AI.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(Intent.createChooser(intent, "Télécharger / Partager l'archive ZIP").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            Toast.makeText(context, "Erreur de partage : ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareFile(context: Context, file: ProjectFile) {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, file.name)
                putExtra(Intent.EXTRA_TEXT, file.content)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Télécharger / Partager ${file.name}").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            Toast.makeText(context, "Erreur : ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }
}
