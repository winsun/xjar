package io.xjar.war;

import io.xjar.*;
import io.xjar.jar.XJarEncryptor;
import io.xjar.key.XKey;
import org.apache.commons.compress.archivers.jar.JarArchiveEntry;
import org.apache.commons.compress.archivers.jar.JarArchiveInputStream;
import org.apache.commons.compress.archivers.jar.JarArchiveOutputStream;

import java.io.*;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.zip.Deflater;

/**
 * Java Web WAR包加密器
 *
 * @author Payne 646742615@qq.com
 * 2018/11/22 15:27
 */
public class XWarEncryptor extends XEntryEncryptor<JarArchiveEntry> implements XEncryptor, XConstants {
    private final int level;

    public XWarEncryptor(XEncryptor xEncryptor) {
        this(xEncryptor, new XWarClassesFilter());
    }

    public XWarEncryptor(XEncryptor xEncryptor, XEntryFilter<JarArchiveEntry> filter) {
        this(xEncryptor, Deflater.DEFLATED, filter);
    }

    public XWarEncryptor(XEncryptor xEncryptor, int level) {
        this(xEncryptor, level, new XWarClassesFilter());
    }

    public XWarEncryptor(XEncryptor xEncryptor, int level, XEntryFilter<JarArchiveEntry> filter) {
        super(xEncryptor, filter);
        this.level = level;
    }

    @Override
    public void encrypt(XKey key, File src, File dest) throws IOException {
        try (
                FileInputStream fis = new FileInputStream(src);
                FileOutputStream fos = new FileOutputStream(dest)
        ) {
            encrypt(key, fis, fos);
        }
    }

    @Override
    public void encrypt(XKey key, InputStream in, OutputStream out) throws IOException {
        JarArchiveInputStream zis = null;
        JarArchiveOutputStream zos = null;
        Set<String> indexes = new LinkedHashSet<>();
        try {
            zis = new JarArchiveInputStream(in);
            zos = new JarArchiveOutputStream(out);
            zos.setLevel(level);
            XUnclosedInputStream nis = new XUnclosedInputStream(zis);
            XUnclosedOutputStream nos = new XUnclosedOutputStream(zos);
            XJarEncryptor xJarEncryptor = new XJarEncryptor(xEncryptor, level);
            JarArchiveEntry entry;
            while ((entry = zis.getNextJarEntry()) != null) {
                if (entry.getName().startsWith(XJAR_SRC_DIR)
                        || entry.getName().endsWith(XJAR_INF_DIR)
                        || entry.getName().endsWith(XJAR_INF_DIR + XJAR_INF_IDX)
                ) {
                    continue;
                }
                if (entry.isDirectory()) {
                    JarArchiveEntry jarArchiveEntry = new JarArchiveEntry(entry.getName());
                    jarArchiveEntry.setTime(entry.getTime());
                    zos.putArchiveEntry(jarArchiveEntry);
                } else if (entry.getName().endsWith(".jar")) {
                    JarArchiveEntry jar = new JarArchiveEntry(entry.getName());
                    jar.setTime(entry.getTime());
                    zos.putArchiveEntry(jar);
                    boolean filtered = filtrate(entry);
                    if (filtered) indexes.add(entry.getName());
                    XEncryptor encryptor = filtered ? xJarEncryptor : xNopEncryptor;
                    encryptor.encrypt(key, nis, nos);
                } else {
                    JarArchiveEntry jarArchiveEntry = new JarArchiveEntry(entry.getName());
                    jarArchiveEntry.setTime(entry.getTime());
                    zos.putArchiveEntry(jarArchiveEntry);
                    boolean filtered = filtrate(entry);
                    if (filtered) indexes.add(entry.getName());
                    XEncryptor encryptor = filtered ? this : xNopEncryptor;
                    try (InputStream eis = encryptor.encrypt(key, nis)) {
                        XKit.transfer(eis, nos);
                    }
                }
                zos.closeArchiveEntry();
            }

            if (!indexes.isEmpty()) {
                JarArchiveEntry XJAR_INF = new JarArchiveEntry(WEB_INF_CLASSES + XJAR_INF_DIR);
                XJAR_INF.setTime(System.currentTimeMillis());
                zos.putArchiveEntry(XJAR_INF);
                zos.closeArchiveEntry();

                JarArchiveEntry IDX = new JarArchiveEntry(WEB_INF_CLASSES + XJAR_INF_DIR + XJAR_INF_IDX);
                IDX.setTime(System.currentTimeMillis());
                zos.putArchiveEntry(IDX);

                for (String index : indexes) {
                    if (index.startsWith(WEB_INF_CLASSES)) {
                        nos.write(index.substring(WEB_INF_CLASSES.length()).getBytes());
                    } else if (index.startsWith(WEB_INF_LIB)) {
                        nos.write(index.substring(WEB_INF_LIB.length()).getBytes());
                    } else {
                        nos.write(index.getBytes());
                    }
                    nos.write(CRLF.getBytes());
                }
                zos.closeArchiveEntry();
            }

            zos.finish();
        } finally {
            XKit.close(zis);
            XKit.close(zos);
        }
    }

    @Override
    public boolean filtrate(JarArchiveEntry entry) {
        return super.filtrate(entry) && (entry.getName().startsWith(WEB_INF_CLASSES) || entry.getName().startsWith(WEB_INF_LIB));
    }
}
