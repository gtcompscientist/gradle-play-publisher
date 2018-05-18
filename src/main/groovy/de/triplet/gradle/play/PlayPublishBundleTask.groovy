package de.triplet.gradle.play

import com.android.builder.model.AppBundleVariantBuildOutput
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.FileContent
import com.google.api.services.androidpublisher.model.ApkListing
import com.google.api.services.androidpublisher.model.Bundle
import com.google.api.services.androidpublisher.model.Track
import org.gradle.api.tasks.TaskAction

class PlayPublishBundleTask extends PlayPublishTask {

    static MIME_TYPE_BUNDLE = 'application/octet-stream'
    static MAX_CHARACTER_LENGTH_FOR_WHATS_NEW_TEXT = 500
    static FILE_NAME_FOR_WHATS_NEW_TEXT = 'whatsnew'

    File inputFolder

    @TaskAction
    publishBundle() {
        publish()

        def versionCodes = variant.outputs
                .findAll { variantOutput -> variantOutput instanceof AppBundleVariantBuildOutput }
                .collect { variantOutput -> publishBundle(new FileContent(MIME_TYPE_BUNDLE, variantOutput.outputFile)) }
                .collect { apk -> apk.getVersionCode() }

        def track = new Track().setVersionCodes(versionCodes)
        if (extension.track == 'rollout') {
            track.setUserFraction(extension.userFraction)
        }
        edits.tracks()
                .update(variant.applicationId, editId, extension.track, track)
                .execute()

        edits.commit(variant.applicationId, editId)
                .execute()
    }

    Bundle publishBundle(bundleFile) {

        def bundle = edits.bundles()
                .upload(variant.applicationId, editId, bundleFile)
                .execute()

        if (extension.untrackOld && extension.track != 'alpha') {
            def untrackChannels = extension.track == 'beta' ? ['alpha'] : ['alpha', 'beta']
            untrackChannels.each { channel ->
                try {
                    def track = edits.tracks().get(variant.applicationId, editId, channel).execute()
                    track.setVersionCodes(track.getVersionCodes().findAll {
                        it > bundle.getVersionCode()
                    })

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
            edits.deobfuscationfiles().upload(variant.applicationId, editId, bundle.getVersionCode(), 'proguard', fileStream).execute()
        }

        if (inputFolder.exists()) {

            // Matches if locale have the correct naming e.g. en-US for play store
            inputFolder.eachDirMatch(matcher) { dir ->
                def whatsNewFile = new File(dir, FILE_NAME_FOR_WHATS_NEW_TEXT + '-' + extension.track)

                if (!whatsNewFile.exists()) {
                    whatsNewFile = new File(dir, FILE_NAME_FOR_WHATS_NEW_TEXT)
                }

                if (whatsNewFile.exists()) {

                    def whatsNewText = TaskHelper.readAndTrimFile(project, whatsNewFile, MAX_CHARACTER_LENGTH_FOR_WHATS_NEW_TEXT, extension.errorOnSizeLimit)
                    def locale = dir.name

                    def newApkListing = new ApkListing().setRecentChanges(whatsNewText)
                    edits.apklistings()
                            .update(variant.applicationId, editId, bundle.getVersionCode(), locale, newApkListing)
                            .execute()
                }
            }
        }

        return bundle
    }

}
