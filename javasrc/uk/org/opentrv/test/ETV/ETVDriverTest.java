/*
The OpenTRV project licenses this file to you
under the Apache Licence, Version 2.0 (the "Licence");
you may not use this file except in compliance
with the Licence. You may obtain a copy of the Licence at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the Licence is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied. See the Licence for the
specific language governing permissions and limitations
under the Licence.

Author(s) / Copyright (s): Damon Hart-Davis 2016
*/

package uk.org.opentrv.test.ETV;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeNotNull;
import static org.junit.Assume.assumeTrue;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import uk.org.opentrv.ETV.driver.ETVSimpleDriverNBulkInputs;
import uk.org.opentrv.ETV.output.ETVHouseholdGroupSimpleSummaryStatsToCSV;
import uk.org.opentrv.ETV.parse.OTLogActivityParse;
import uk.org.opentrv.hdd.HDDUtil;
import uk.org.opentrv.test.hdd.DDNExtractorTest;

public class ETVDriverTest
    {
    /**Private temp directory to do work in created for each test and cleared down after; never null during tests. */
    private Path tempDir;

    @Before
    public void before() throws Exception
        {
        tempDir = Files.createTempDirectory(null);
        }

    @After
    public void after() throws Exception
        {
        // Delete the directory tree.
        Files.walkFileTree(tempDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return(FileVisitResult.CONTINUE);
            }
            @Override
            public FileVisitResult postVisitDirectory(final Path dir, final IOException exc) throws IOException {
                Files.delete(dir);
                return(FileVisitResult.CONTINUE);
            }
        });
        // Check that it has been cleared.
        if(tempDir.toFile().exists()) { throw new AssertionError("cleanup failed: " + tempDir); }
        tempDir = null;
        }

    /**Helper. */
    private static int cpReaderToWriter(final Reader input, final Writer output) throws IOException
        {
        final char buf[] = new char[8192];
        int len = 0;
        int n = 0;
        while(-1 != (n = input.read(buf)))
            {
            output.write(buf, 0, n);
            len += n;
            }
        return(len);
        }

    /**Test for correct processing of simple single-house data set. */
    @Test public void testWithSingleHouseDataSet() throws IOException
        {
        final File inDir = tempDir.toFile();
        final File outDir = tempDir.toFile();
        // Copy HDD file.
        try(final Reader r = DDNExtractorTest.getETVEGLLHDD2016H1CSVReader())
            {
            try(final FileWriter w = new FileWriter(new File(inDir, ETVSimpleDriverNBulkInputs.INPUT_FILE_HDD)))
                { cpReaderToWriter(r, w); }
            }
        // Copy sample household data file.
        try(final Reader r = ETVParseTest.getNBulkSH2016H1CSVReader())
            {
            try(final FileWriter w = new FileWriter(new File(inDir, ETVSimpleDriverNBulkInputs.INPUT_FILE_NKWH)))
                { cpReaderToWriter(r, w); }
            }
        // Ensure no old result files hanging around...
        final File basicResultFile = new File(outDir, ETVSimpleDriverNBulkInputs.OUTPUT_STATS_FILE_BASIC);
        basicResultFile.delete(); // Make sure no output file.
        assertFalse("output file should not yet exist", basicResultFile.isFile());
        final File basicFilteredResultFile = new File(outDir, ETVSimpleDriverNBulkInputs.OUTPUT_STATS_FILE_FILTERED_BASIC);
        basicFilteredResultFile.delete(); // Make sure no output file.
        assertFalse("output filtered file should not yet exist", basicFilteredResultFile.isFile());
        // Do the computation...
        ETVSimpleDriverNBulkInputs.doComputation(inDir, outDir);
        // Check results.
        assertTrue("output file should now exist", basicResultFile.isFile());
        assertTrue("output filtered file should now exist", basicFilteredResultFile.isFile());
        final String expected =
            "\"house ID\",\"slope energy/HDD\",\"baseload energy\",\"R^2\",\"n\",\"efficiency gain if computed\"\n" +
            "\"5013\",1.5532478,1.3065631,0.62608224,156,\n";
        final String actualBasic = new String(Files.readAllBytes(basicResultFile.toPath()), "ASCII7");
        assertEquals(expected, actualBasic);
        final String actualFilteredBasic = new String(Files.readAllBytes(basicFilteredResultFile.toPath()), "ASCII7");
        assertEquals(expected, actualFilteredBasic);
        }

    /**Test for correct processing with segmentation (of single device/house). */
    @Test public void testWithSegmentation() throws IOException
        {
        final File inDir = tempDir.toFile();
        final File outDir = tempDir.toFile();
        // Copy HDD file.
        try(final Reader r = DDNExtractorTest.getETVEGLLHDD2016H1CSVReader())
            {
            try(final FileWriter w = new FileWriter(new File(inDir, ETVSimpleDriverNBulkInputs.INPUT_FILE_HDD)))
                { cpReaderToWriter(r, w); }
            }
        // Copy sample household data file.
        try(final Reader r = ETVParseTest.getNBulkSH2016H1CSVReader())
            {
            try(final FileWriter w = new FileWriter(new File(inDir, ETVSimpleDriverNBulkInputs.INPUT_FILE_NKWH)))
                { cpReaderToWriter(r, w); }
            }
        // Copy valve logs (from compressed to uncompressed form in, in this case).
        try(final Reader r = HDDUtil.getGZIPpedASCIIResourceReader(ETVParseTest.class, ETVParseTest.VALVE_LOG_SAMPLE_DIR + "/2d1a.json.gz"))
            {
            try(final FileWriter w = new FileWriter(new File(inDir, "3015.json")))
                { cpReaderToWriter(r, w); }
            }
        // Create (trivial) grouping...
        try(final Reader r = new StringReader("5013,3015"))
            {
            try(final FileWriter w = new FileWriter(new File(inDir, OTLogActivityParse.LOGDIR_PATH_TO_GROUPING_CSV)))
                { cpReaderToWriter(r, w); }
            }

        // Ensure no old result files hanging around...
        final File basicResultFile = new File(outDir, ETVSimpleDriverNBulkInputs.OUTPUT_STATS_FILE_BASIC);
        basicResultFile.delete(); // Make sure no output file.
        assertFalse("output file should not yet exist", basicResultFile.isFile());
        final File basicFilteredResultFile = new File(outDir, ETVSimpleDriverNBulkInputs.OUTPUT_STATS_FILE_FILTERED_BASIC);
        basicFilteredResultFile.delete(); // Make sure no output file.
        assertFalse("output filtered file should not yet exist", basicFilteredResultFile.isFile());
        final File segmentedResultFile = new File(outDir, ETVSimpleDriverNBulkInputs.OUTPUT_STATS_FILE_SEGMENTED);
        segmentedResultFile.delete(); // Make sure no output file.
        assertFalse("output segmented file should not yet exist", segmentedResultFile.isFile());
        final File summaryResultFile = new File(outDir, ETVSimpleDriverNBulkInputs.OUTPUT_STATS_FILE_MULITHOUSEHOLD_SUMMARY);
        summaryResultFile.delete(); // Make sure no output file.
        assertFalse("output summary file should not yet exist", summaryResultFile.isFile());
        // Do the computation...
        ETVSimpleDriverNBulkInputs.doComputation(inDir, outDir);
        // Check results.
        assertTrue("output file should now exist", basicResultFile.isFile());
        assertTrue("output filtered file should now exist", basicFilteredResultFile.isFile());
        assertTrue("output segmented file should now exist", segmentedResultFile.isFile());
        assertTrue("output summary file should now exist", summaryResultFile.isFile());
        final String expected =
            "\"house ID\",\"slope energy/HDD\",\"baseload energy\",\"R^2\",\"n\",\"efficiency gain if computed\"\n" +
            "\"5013\",1.5532478,1.3065631,0.62608224,156,\n";
        final String actualBasic = new String(Files.readAllBytes(basicResultFile.toPath()), "ASCII7");
        assertEquals(expected, actualBasic);
        final String actualFilteredBasic = new String(Files.readAllBytes(basicFilteredResultFile.toPath()), "ASCII7");
        assertEquals(expected, actualFilteredBasic);
        final String expectedS =
            "\"house ID\",\"slope energy/HDD\",\"baseload energy\",\"R^2\",\"n\",\"efficiency gain if computed\"\n" +
            "\"5013\",1.138506,5.764153,0.24607657,10,1.9855182\n";
        final String actualSegmented = new String(Files.readAllBytes(segmentedResultFile.toPath()), "ASCII7");
//System.out.println(actualSegmented);
        assertEquals(expectedS, actualSegmented);
        final String expectedSummary =
            ETVHouseholdGroupSimpleSummaryStatsToCSV.headerCSV + '\n' +
            "1,1,10,0.24607657,0.0,1.138506,0.0,1.9855182,0.0\n";
        final String actualSummary = new String(Files.readAllBytes(summaryResultFile.toPath()), "ASCII7");
System.out.println(actualSummary);
        assertEquals(expectedSummary, actualSummary);
        // TODO: verify textual report if any?
        }

    /**Input directory in home dir for testWithExternalDataSet(). */
    public static final String fixedDataSetDir = "ETV-prepared-data";
    /**Output directory in home dir for testWithExternalDataSet(). */
    public static final String fixedDataSetOutDir = "ETV-prepared-data-out";

    /**Test for robust driver behaviour on external data set if any.
     * Will not run if input data directory is missing.
     * <p>
     * Will fail for other errors.
     * <p>
     * Simply check that the output file gets generated.
     */
    @Test public void testWithExternalDataSet() throws IOException
        {
        final String homeDir = System.getProperty("user.home");
        assumeNotNull(null != homeDir);
        final File inDir = new File(new File(homeDir), fixedDataSetDir);
        assumeTrue(inDir.isDirectory());
        final File outDir = new File(new File(homeDir), fixedDataSetOutDir);
        assertTrue(outDir.isDirectory());
        assertTrue(outDir.canWrite());
        // Ensure no old result files hanging around...
        final File basicResultFile = new File(outDir, ETVSimpleDriverNBulkInputs.OUTPUT_STATS_FILE_BASIC);
        basicResultFile.delete(); // Make sure no output file.
        assertFalse("output file should not yet exist", basicResultFile.isFile());
        final File basicFilteredResultFile = new File(outDir, ETVSimpleDriverNBulkInputs.OUTPUT_STATS_FILE_FILTERED_BASIC);
        basicFilteredResultFile.delete(); // Make sure no output file.
        assertFalse("output filtered file should not yet exist", basicFilteredResultFile.isFile());
        // Do the computation.
        ETVSimpleDriverNBulkInputs.doComputation(inDir, outDir);
        // Check results.
        assertTrue("output file should now exist", basicResultFile.isFile());
        assertTrue("output filtered file should now exist", basicFilteredResultFile.isFile());
        System.out.println("Written to " + outDir);
        }
    }
