package dev.bikram.filepipe.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class FileScanOptimizationTest {
    @Test
    fun diskTraversalReportsAndStopsAtDepthLimit() {
        val rootDirectory = Files.createTempDirectory("filepipe-scan").toFile()
        try {
            val rootFile = rootDirectory.resolve("root.txt").apply { writeText("root") }
            val firstLevelDirectory = rootDirectory.resolve("first").apply { mkdirs() }
            val firstLevelFile = firstLevelDirectory.resolve("first.txt").apply { writeText("first") }
            val secondLevelDirectory = firstLevelDirectory.resolve("second").apply { mkdirs() }
            secondLevelDirectory.resolve("second.txt").writeText("second")
            var depthTruncated = false

            val scannedFiles =
                walkDiskFilesWithRelativeParents(
                    dir = rootDirectory,
                    maxDepth = 2,
                    relativeParentSegments = emptyList(),
                    onDepthLimitReached = { depthTruncated = true },
                ).toList()

            assertEquals(setOf(rootFile, firstLevelFile), scannedFiles.map { result -> result.first }.toSet())
            assertTrue(depthTruncated)
        } finally {
            rootDirectory.deleteRecursively()
        }
    }

    @Test
    fun diskTraversalDoesNotReportTruncationForFullyVisitedTree() {
        val rootDirectory = Files.createTempDirectory("filepipe-scan").toFile()
        try {
            rootDirectory.resolve("first").apply {
                mkdirs()
                resolve("file.txt").writeText("content")
            }
            var depthTruncated = false

            walkDiskFilesWithRelativeParents(
                dir = rootDirectory,
                maxDepth = 2,
                relativeParentSegments = emptyList(),
                onDepthLimitReached = { depthTruncated = true },
            ).toList()

            assertFalse(depthTruncated)
        } finally {
            rootDirectory.deleteRecursively()
        }
    }

    @Test
    fun diskTraversalPrunesTheExcludedDestinationSubtree() {
        val rootDirectory = Files.createTempDirectory("filepipe-scan").toFile().canonicalFile
        try {
            val rootFile = rootDirectory.resolve("root.jpg").apply { writeText("root") }
            val siblingFile =
                rootDirectory.resolve("folder1").resolve("keep.jpg").apply {
                    parentFile!!.mkdirs()
                    writeText("keep")
                }
            val destinationDirectory = rootDirectory.resolve("folder1").resolve("sub-folder2").apply { mkdirs() }
            destinationDirectory.resolve("delivered.jpg").writeText("delivered")
            destinationDirectory.resolve("deeper").apply {
                mkdirs()
                resolve("nested.jpg").writeText("nested")
            }

            val scannedFiles =
                walkDiskFilesWithRelativeParents(
                    dir = rootDirectory,
                    maxDepth = MAX_SCAN_DEPTH,
                    relativeParentSegments = emptyList(),
                    excludedDirPath = destinationDirectory.path,
                ).toList()

            assertEquals(setOf(rootFile, siblingFile), scannedFiles.map { result -> result.first }.toSet())
        } finally {
            rootDirectory.deleteRecursively()
        }
    }

    @Test
    fun diskTraversalPrunesTrailingSlashExcludedFoldersAndNestedSubfolders() {
        val rootDirectory = Files.createTempDirectory("filepipe-scan-exclude").toFile().canonicalFile
        try {
            val keepRoot = rootDirectory.resolve("photo.jpg").apply { writeText("keep") }
            val keepCamera =
                rootDirectory.resolve("Camera").resolve("IMG_001.jpg").apply {
                    parentFile!!.mkdirs()
                    writeText("keep")
                }
            val keepThumbFile =
                rootDirectory.resolve("Camera").resolve("thumb_001.jpg").apply {
                    writeText("thumb")
                }

            listOf(
                rootDirectory.resolve(".thumbnails").resolve("a.jpg"),
                rootDirectory
                    .resolve("Camera")
                    .resolve("thumbnails")
                    .resolve("nested")
                    .resolve("b.jpg"),
                rootDirectory
                    .resolve("Movies")
                    .resolve("cache")
                    .resolve("deep")
                    .resolve("c.mp4"),
            ).forEach { excludedFile ->
                excludedFile.parentFile!!.mkdirs()
                excludedFile.writeText("excluded")
            }

            val patterns = listOf(".thumbnails/", "thumbnails/", "cache/", "thumb*.*")
            val filterContext =
                FileScanFilterContext(
                    extensions = listOf("*"),
                    filenameRegexes = null,
                    isRegexPattern = false,
                    excludeRegexes = buildExcludeRegexes(patterns, isRegexPattern = false),
                    isExcludeRegexPattern = false,
                    minFileSizeBytes = null,
                    maxFileSizeBytes = null,
                    minAgeMs = null,
                    maxAgeMs = null,
                    nowMs = 0L,
                    excludeFolderPatterns = buildExcludeFolderPatterns(patterns, isRegexPattern = false),
                )

            val scannedFiles =
                walkDiskFilesWithRelativeParents(
                    dir = rootDirectory,
                    maxDepth = MAX_SCAN_DEPTH,
                    relativeParentSegments = emptyList(),
                    shouldExcludeDirectory = { relativeSegments ->
                        shouldExcludeFolder(
                            relativeSegments = relativeSegments,
                            excludeFolderPatterns = filterContext.excludeFolderPatterns,
                            isRegexPattern = false,
                        )
                    },
                ).filter { (file, _) ->
                    passesCheapScanFilters(
                        fileName = file.name,
                        fileSizeBytes = file.length(),
                        lastModifiedMs = file.lastModified(),
                        filters = filterContext,
                    )
                }.map { it.first }
                    .toSet()

            assertEquals(setOf(keepRoot, keepCamera), scannedFiles)
            assertFalse(keepThumbFile in scannedFiles)
        } finally {
            rootDirectory.deleteRecursively()
        }
    }

    @Test
    fun destinationSegmentsUseOneNormalizedCacheIdentity() {
        assertEquals(
            listOf("Pictures", "Trips"),
            normalizeDestinationParentSegments(listOf(" Pictures ", ".", "", "..", "Trips")),
        )
    }
}
