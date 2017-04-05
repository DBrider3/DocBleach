package xyz.docbleach.modules.ole2;

import org.apache.poi.poifs.filesystem.DocumentEntry;
import org.apache.poi.poifs.filesystem.Entry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xyz.docbleach.api.BleachSession;
import xyz.docbleach.api.BleachTestBase;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static xyz.docbleach.api.IBleachSession.SEVERITY.*;

class OLE2BleachTest extends BleachTestBase {
    private static final String SUMMARY_INFORMATION_ENTRY_NAME = "\005SummaryInformation";
    private OLE2Bleach instance;
    private BleachSession session;

    @BeforeEach
    void setUp() {
        instance = spy(new OLE2Bleach());
        session = mock(BleachSession.class);
    }


    @Test
    void removeTemplate() {
        Predicate<Entry> predicate = instance.removeTemplate(session);

        // Test an invalid stream, should be ignored
        Entry entry = mock(Entry.class);
        doReturn("\005RandomString").when(entry).getName();
        assertTrue(predicate.test(entry));
        verify(instance, never()).sanitizeDocumentEntry(eq(session), any());

        // Test a valid stream name, but wrong type (should be ignored)
        reset(entry);
        doReturn(SUMMARY_INFORMATION_ENTRY_NAME).when(entry).getName();
        assertTrue(predicate.test(entry));
        verify(instance, never()).sanitizeDocumentEntry(eq(session), any());

        reset(instance, entry);

        // Test a valid SummaryInformation name
        DocumentEntry docEntry = mock(DocumentEntry.class);

        when(docEntry.getName()).thenReturn(SUMMARY_INFORMATION_ENTRY_NAME);
        doNothing().when(instance).sanitizeDocumentEntry(session, docEntry);
        assertTrue(predicate.test(docEntry));
        verify(instance, atLeastOnce()).sanitizeDocumentEntry(session, docEntry);
    }

    @Test
    void getTemplateSeverity() {
        assertEquals(instance.getTemplateSeverity("Normal.dotm"), MEDIUM, "The base template");

        assertEquals(instance.getTemplateSeverity("my-template.dotm"), HIGH, "Unknown template");
        assertEquals(instance.getTemplateSeverity("hxxp://my-template.dotm"), HIGH, "Unknown template");

        assertEquals(instance.getTemplateSeverity("https://google.com"), EXTREME, "Detects links");
        assertEquals(instance.getTemplateSeverity("http://google.com"), EXTREME, "Detects links");
        assertEquals(instance.getTemplateSeverity("ftp://google.com"), EXTREME, "Detects links");

    }

    @Test
    void removeMacros() {
        Predicate<Entry> predicate = instance.removeMacros(session);

        Entry entry = mock(Entry.class);
        doReturn("_VBA_PROJECT_CUR").when(entry).getName();
        assertFalse(predicate.test(entry));
        assertThreatsFound(session, 1, EXTREME);
        reset(session);

        doReturn(SUMMARY_INFORMATION_ENTRY_NAME).when(entry).getName();
        assertTrue(predicate.test(entry));
        assertThreatsFound(session, 0);
        reset(session);

        doReturn("RandomName").when(entry).getName();
        assertTrue(predicate.test(entry));
        assertThreatsFound(session, 0);
        reset(session);
    }


    @Test
    void handlesMagic() throws IOException {
        Charset charset = Charset.defaultCharset();
        // Check that empty does not trigger an error
        InputStream invalidInputStream = new ByteArrayInputStream("".getBytes(charset));
        assertFalse(instance.handlesMagic(invalidInputStream));

        // Check that this bleach is sane
        invalidInputStream = new ByteArrayInputStream("Anything".getBytes(charset));
        assertFalse(instance.handlesMagic(invalidInputStream));
    }
}