package de.triplet.gradle.play

import com.google.api.client.http.FileContent
import com.google.api.services.androidpublisher.AndroidPublisher
import com.google.api.services.androidpublisher.model.AppEdit
import com.google.api.services.androidpublisher.model.Bundle
import com.google.api.services.androidpublisher.model.Track
import com.google.api.services.androidpublisher.model.TrackRelease
import org.hamcrest.Description
import org.hamcrest.TypeSafeMatcher
import org.junit.Before
import org.junit.Test
import org.mockito.Mock

import static org.junit.Assert.assertFalse
import static org.junit.Assert.assertTrue
import static org.mockito.Matchers.any
import static org.mockito.Matchers.anyString
import static org.mockito.Matchers.argThat
import static org.mockito.Matchers.eq
import static org.mockito.Mockito.doReturn
import static org.mockito.Mockito.times
import static org.mockito.Mockito.verify
import static org.mockito.MockitoAnnotations.initMocks

class PlayPublishBundleTaskTest {

    @Mock
    AndroidPublisher publisherMock

    @Mock
    AndroidPublisher.Edits editsMock

    @Mock
    AndroidPublisher.Edits.Insert insertMock

    @Mock
    AndroidPublisher.Edits.Tracks editTracksMock

    @Mock
    AndroidPublisher.Edits.Commit editCommitMock

    @Mock
    AndroidPublisher.Edits.Tracks.Update tracksUpdateMock

    @Mock
    AndroidPublisher.Edits.Tracks.Get getAlphaTrackMock

    @Mock
    AndroidPublisher.Edits.Tracks.Get getBetaTrackMock

    @Mock
    AndroidPublisher.Edits.Bundles bundlesMock

    @Mock
    AndroidPublisher.Edits.Bundles.Upload uploadMock

    // These are final and not mock able
    final AppEdit appEdit = new AppEdit()
    final Bundle bundle = new Bundle()
    final Track alphaTrack = new Track()
    final Track betaTrack = new Track()

    @Before
    void setup() {
        initMocks(this)

        appEdit.setId('424242')
        bundle.setVersionCode(42)

        doReturn(editsMock).when(publisherMock).edits()
        doReturn(insertMock).when(editsMock).insert(anyString(), any(AppEdit.class))
        doReturn(appEdit).when(insertMock).execute()

        doReturn(bundlesMock).when(editsMock).bundles()
        doReturn(uploadMock).when(bundlesMock).upload(anyString(), anyString(), any(FileContent.class))
        doReturn(bundle).when(uploadMock).execute()

        doReturn(editTracksMock).when(editsMock).tracks()
        doReturn(getAlphaTrackMock).when(editTracksMock).get(anyString(), anyString(), eq('alpha'))
        doReturn(alphaTrack).when(getAlphaTrackMock).execute()
        doReturn(getBetaTrackMock).when(editTracksMock).get(anyString(), anyString(), eq('beta'))
        doReturn(betaTrack).when(getBetaTrackMock).execute()

        doReturn(tracksUpdateMock).when(editTracksMock).update(anyString(), anyString(), anyString(), any(Track.class))
        doReturn(editCommitMock).when(editsMock).commit(anyString(), anyString())
    }

    @Test
    void testApplicationId() {
        def project = TestHelper.evaluatableProject()
        project.evaluate()

        // Attach the mock
        project.tasks.publishBundleRelease.service = publisherMock

        // finally run the task we want to check
        project.tasks.publishBundleRelease.publish()

        // verify that we init the connection with the correct application id
        verify(editsMock).insert('com.example.publisher', null)
    }

    @Test
    void testApplicationIdWithFlavorsAndSuffix() {
        def project = TestHelper.evaluatableProject()

        project.android {
            flavorDimensions 'pricing'

            productFlavors {
                paid {
                    dimension 'pricing'
                    applicationId 'com.example.publisher.paid'
                }
                free {
                    dimension 'pricing'
                }
            }

            buildTypes {
                release {
                    applicationIdSuffix '.release'
                }
            }
        }

        project.evaluate()

        // Attach the mock
        project.tasks.publishBundlePaidRelease.service = publisherMock

        // finally run the task we want to check
        project.tasks.publishBundlePaidRelease.publish()

        // verify that we init the connection with the correct application id
        verify(editsMock).insert('com.example.publisher.paid.release', null)
    }

    @Test
    void whenPublishingToBeta_publishBundleRelease_removesBlockingVersionsFromAlpha() {
        def project = TestHelper.evaluatableProject()
        project.play {
            track 'beta'
            untrackOld true
        }

        project.evaluate()

        alphaTrack.setReleases([new TrackRelease().setVersionCodes([41, 40])])

        project.tasks.publishBundleRelease.service = publisherMock
        project.tasks.publishBundleRelease.publishBundle()

        verify(editTracksMock).update(
                eq('com.example.publisher'),
                eq('424242'),
                eq('alpha'),
                emptyTrack())
    }

    @Test
    void whenPublishingToBeta_publishBundleRelease_doesNotRemoveNonBlockingVersionsFromAlpha() {
        def project = TestHelper.evaluatableProject()
        project.play {
            track 'beta'
            untrackOld true
        }
        project.evaluate()

        alphaTrack.setReleases([new TrackRelease().setVersionCodes([43])])

        project.tasks.publishBundleRelease.service = publisherMock
        project.tasks.publishBundleRelease.publishBundle()

        verify(editTracksMock).update(
                eq('com.example.publisher'),
                eq('424242'),
                eq('alpha'),
                trackThatContains(43))
    }

    @Test
    void whenPublishingToProduction_publishBundleRelease_removesBlockingVersionFromAlphaAndBeta() {
        def project = TestHelper.evaluatableProject()
        project.play {
            track 'production'
            untrackOld true
        }

        project.evaluate()

        alphaTrack.setReleases([new TrackRelease().setVersionCodes([41, 40])])
        betaTrack.setReleases([new TrackRelease().setVersionCodes([39])])

        project.tasks.publishBundleRelease.service = publisherMock
        project.tasks.publishBundleRelease.publishBundle()

        verify(editTracksMock).update(
                eq('com.example.publisher'),
                eq('424242'),
                eq('alpha'),
                emptyTrack())

        verify(editTracksMock).update(
                eq('com.example.publisher'),
                eq('424242'),
                eq('beta'),
                emptyTrack())
    }

    @Test
    void whenPublishingToProduction_publishBundleRelease_doesNotRemoveNonBlockingVersionFromAlphaOrBeta() {
        def project = TestHelper.evaluatableProject()
        project.play {
            track 'production'
            untrackOld true
        }
        project.evaluate()

        alphaTrack.setReleases([new TrackRelease().setVersionCodes([44])])
        betaTrack.setReleases([new TrackRelease().setVersionCodes([43])])

        project.tasks.publishBundleRelease.service = publisherMock
        project.tasks.publishBundleRelease.publishBundle()

        verify(editTracksMock).update(
                eq('com.example.publisher'),
                eq('424242'),
                eq('alpha'),
                trackThatContains(44))

        verify(editTracksMock).update(
                eq('com.example.publisher'),
                eq('424242'),
                eq('beta'),
                trackThatContains(43))
    }

    @Test
    void whenFlagNotSet_publishBundleRelease_doesNotTouchOtherTracks() {
        def project = TestHelper.evaluatableProject()
        project.play {
            track 'production'
            untrackOld false
        }
        project.evaluate()

        verify(editTracksMock, times(0)).update(anyString(), anyString(), eq('alpha'), any(Track.class))
        verify(editTracksMock, times(0)).update(anyString(), anyString(), eq('beta'), any(Track.class))
    }

    @Test
    void testMatcher() {
        def pattern = PlayPublishTask.matcher

        assertTrue(pattern.matcher('de-DE').find())
        assertTrue(pattern.matcher('de').find())
        assertTrue(pattern.matcher('es-419').find())
        assertTrue(pattern.matcher('fil').find())

        assertFalse(pattern.matcher('de_DE').find())
        assertFalse(pattern.matcher('fil-PH').find())
    }

    @Test
    void testApplicationIdChange() {
        def project = TestHelper.evaluatableProject()

        project.android {
            flavorDimensions 'pricing'

            productFlavors {
                paid {
                    dimension 'pricing'
                    applicationId 'com.example.publisher'
                }
                free {
                    dimension 'pricing'
                }
            }

            buildTypes {
                release {
                    applicationIdSuffix '.release'
                }
            }

            applicationVariants.all { variant ->
                def flavorName = variant.variantData.variantConfiguration.flavorName
                if (flavorName == 'paid') {
                    variant.mergedFlavor.applicationId += '.paid'
                }
            }
        }

        project.evaluate()

        // Attach the mock
        project.tasks.publishBundlePaidRelease.service = publisherMock

        // finally run the task we want to check
        project.tasks.publishBundlePaidRelease.publish()

        // verify that we init the connection with the correct application id
        verify(editsMock).insert('com.example.publisher.paid.release', null)
    }

    static Track emptyTrack() {
        return argThat(new TypeSafeMatcher<Track>() {
            @Override
            protected boolean matchesSafely(Track track) {
                return track.getReleases().sum { (it as TrackRelease).getVersionCodes().size() } == 0
            }

            @Override
            void describeTo(Description description) {

            }
        })
    }

    static Track trackThatContains(final int code) {
        return argThat(new TypeSafeMatcher<Track>() {
            @Override
            protected boolean matchesSafely(Track track) {
                return track.getReleases().find {it.getVersionCodes().contains(code)} != null
            }

            @Override
            void describeTo(Description description) {

            }
        })
    }
}