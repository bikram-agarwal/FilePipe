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
    fun destinationSegmentsUseOneNormalizedCacheIdentity() {
        assertEquals(
            listOf("Pictures", "Trips"),
            normalizeDestinationParentSegments(listOf(" Pictures ", ".", "", "..", "Trips")),
        )
    }
}
