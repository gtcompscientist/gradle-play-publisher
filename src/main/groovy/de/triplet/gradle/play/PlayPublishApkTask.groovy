package de.triplet.gradle.play

import com.android.build.gradle.api.ApkVariantOutput
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.FileContent
import com.google.api.services.androidpublisher.model.Apk
import com.google.api.services.androidpublisher.model.LocalizedText
import com.google.api.services.androidpublisher.model.Track
import com.google.api.services.androidpublisher.model.TrackRelease
import org.gradle.api.tasks.TaskAction

class PlayPublishApkTask extends PlayPublishTask {

    static MIME_TYPE_APK = 'application/vnd.android.package-archive'
    static MAX_CHARACTER_LENGTH_FOR_WHATS_NEW_TEXT = 500
    static FILE_NAME_FOR_WHATS_NEW_TEXT = 'whatsnew'

    File inputFolder

    @TaskAction
    publishApks() {
        publish()

        def versionCodes = variant.outputs
                .findAll { variantOutput -> variantOutput instanceof ApkVariantOutput }
                .collect { variantOutput -> publishApk(new FileContent(MIME_TYPE_APK, variantOutput.outputFile)) }
                .collect { apk -> apk.getVersionCode() }

        def track = new Track()
        def release = new TrackRelease()

        release.setVersionCodes(versionCodes)

        if (extension.track == 'rollout') {
            release.setUserFraction(extension.userFraction)
            release.setStatus('inProgress')
        } else {
            release.setStatus('completed')
        }

        if (inputFolder.exists()) {
            def releaseNotes = new ArrayList<LocalizedText>()

            // Matches if locale have the correct naming e.g. en-US for play store
            inputFolder.eachDirMatch(matcher) { dir ->
                def whatsNewFile = new File(dir, FILE_NAME_FOR_WHATS_NEW_TEXT + '-' + extension.track)

                if (!whatsNewFile.exists()) {
                    whatsNewFile = new File(dir, FILE_NAME_FOR_WHATS_NEW_TEXT)
                }

                if (whatsNewFile.exists()) {

                    def whatsNewText = TaskHelper.readAndTrimFile(project, whatsNewFile, MAX_CHARACTER_LENGTH_FOR_WHATS_NEW_TEXT, extension.errorOnSizeLimit)
                    def locale = dir.name

                    releaseNotes.add(
                            new LocalizedText().setText(whatsNewText).setLanguage(locale)
                    )
                }
            }

            release.setReleaseNotes(releaseNotes)
        }

        track.setReleases([release])

        edits.tracks()
                .update(variant.applicationId, editId, extension.track, track)
                .execute()

        edits.commit(variant.applicationId, editId)
                .execute()
    }

    Apk publishApk(apkFile) {

        def apk = edits.apks()
                .upload(variant.applicationId, editId, apkFile)
                .execute()

        if (extension.untrackOld && extension.track != 'alpha') {
            def untrackChannels = extension.track == 'beta' ? ['alpha'] : ['alpha', 'beta']
            untrackChannels.each { channel ->
                try {
                    def track = edits.tracks().get(variant.applicationId, editId, channel).execute()

                    track.getReleases().each { release ->
                        release.setVersionCodes(release.getVersionCodes().findAll {
                            it > apk.getVersionCode()
                        })
                    }

                    edits.tracks().update(variant.applicationId, editId, channel, track).execute()
                } catch (GoogleJsonResponseException e) {
                    // Just skip if there is no version in track
                    if (e.details.getCode() != 404) {
                        throw e
                    }
                }
            }
        }

        // Upload Proguard mapping.txt if available
        if (variant.mappingFile?.exists()) {
            def fileStream = new FileContent('application/octet-stream', variant.mappingFile)
            edits.deobfuscationfiles().upload(variant.applicationId, editId, apk.getVersionCode(), 'proguard', fileStream).execute()
        }

        return apk
    }

}
