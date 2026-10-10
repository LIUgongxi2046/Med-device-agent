package com.ruoyi.medical.identity;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.attribute.UserPrincipal;
import java.util.Arrays;
import java.util.Objects;
import jnr.constants.platform.Errno;
import jnr.constants.platform.OpenFlags;
import jnr.ffi.LibraryLoader;
import jnr.ffi.annotations.SaveError;
import jnr.posix.FileStat;
import jnr.posix.NanosecondFileStat;
import jnr.posix.POSIX;
import jnr.posix.POSIXFactory;

final class ProtectedConfigurationFile {
    private final Path directory;
    private final String name;
    private final long ownerUid;
    private final POSIX posix;
    private final OpenAt openAt;
    private final int fileFlags;
    private final int directoryFlags;
    private final long maximumBytes;

    ProtectedConfigurationFile(Path directory, UserPrincipal owner, String name, long maximumBytes) {
        this.directory = Objects.requireNonNull(directory).toAbsolutePath().normalize();
        Objects.requireNonNull(name);
        if (name.isEmpty() || !name.equals(Path.of(name).getFileName().toString())
                || Path.of(name).getNameCount() != 1 || name.equals(".") || name.equals("..")) {
            throw new IllegalArgumentException("PROTECTED_CONFIGURATION_NAME_REQUIRED");
        }
        this.name = name;
        if (maximumBytes <= 0 || maximumBytes >= Integer.MAX_VALUE) {
            throw new IllegalArgumentException("PROTECTED_CONFIGURATION_CAPACITY_REQUIRED");
        }
        this.maximumBytes = maximumBytes;
        posix = Objects.requireNonNull(POSIXFactory.getNativePOSIX(), "PROTECTED_CONFIGURATION_NATIVE_POSIX_REQUIRED");
        if (!posix.isNative()) throw new SecurityException("PROTECTED_CONFIGURATION_NATIVE_POSIX_REQUIRED");
        var account = Objects.requireNonNull(posix.getpwnam(Objects.requireNonNull(owner).getName()), "PROTECTED_CONFIGURATION_OWNER_REQUIRED");
        ownerUid = account.getUID();
        openAt = LibraryLoader.create(OpenAt.class).failImmediately().load(POSIXFactory.STANDARD_C_LIBRARY_NAME);
        fileFlags = flags(OpenFlags.O_RDONLY, OpenFlags.O_NOFOLLOW, OpenFlags.O_CLOEXEC, OpenFlags.O_NONBLOCK);
        directoryFlags = fileFlags | flags(OpenFlags.O_DIRECTORY);
    }

    byte[] read() throws IOException {
        var expectedDirectory = directoryAttributes();
        byte[] body;
        try (var opened = descriptor(posix.open(directory.toString(), directoryFlags, 0))) {
            requireSame(expectedDirectory, attributes(opened));
            // openat固定已经打开的目录，fstat核查实际文件与当前文件名指向。
            try (var file = descriptor(openAt.openat(opened.value, name, fileFlags, 0))) {
                var before = attributes(file); requireFile(before);
                byte[] bytes = new byte[Math.toIntExact(before.st_size() + 1)];
                byte[] chunk = new byte[Math.min(bytes.length, 8192)];
                int offset = 0;
                while (offset < bytes.length) {
                    long count = posix.read(file.value, chunk, (long) Math.min(chunk.length, bytes.length - offset));
                    if (count < 0) throw nativeFailure("PROTECTED_CONFIGURATION_READ_FAILED");
                    if (count == 0) break;
                    int length = Math.toIntExact(count);
                    System.arraycopy(chunk, 0, bytes, offset, length); offset += length;
                }
                if (offset != before.st_size()) throw new SecurityException("PROTECTED_CONFIGURATION_INPUT_CHANGED");
                body = Arrays.copyOf(bytes, offset);
                requireSame(before, attributes(file));
                try (var current = descriptor(openAt.openat(opened.value, name, fileFlags, 0))) {
                    requireSame(before, attributes(current));
                }
            }
            requireSame(expectedDirectory, attributes(opened));
            requireSame(expectedDirectory, directoryAttributes());
        }
        return body;
    }

    private FileStat directoryAttributes() throws IOException {
        if (!directory.equals(directory.toRealPath())) throw new SecurityException("PROTECTED_CONFIGURATION_DIRECTORY_IDENTITY_REQUIRED");
        var attributes = posix.allocateStat();
        if (posix.lstat(directory.toString(), attributes) != 0) throw nativeFailure("PROTECTED_CONFIGURATION_DIRECTORY_STAT_FAILED");
        if (!attributes.isDirectory() || Integer.toUnsignedLong(attributes.uid()) != ownerUid
                || attributes.ino() == 0 || (attributes.mode() & 07777) != 0700) {
            throw new SecurityException("PROTECTED_CONFIGURATION_PRIVATE_DIRECTORY_REQUIRED");
        }
        return attributes;
    }

    private void requireFile(FileStat attributes) {
        if (!attributes.isFile() || Integer.toUnsignedLong(attributes.uid()) != ownerUid || attributes.ino() == 0
                || (attributes.mode() & 07777) != 0600 || attributes.nlink() != 1
                || attributes.st_size() <= 0 || attributes.st_size() > maximumBytes) {
            throw new SecurityException("PROTECTED_CONFIGURATION_PRIVATE_FILE_REQUIRED");
        }
    }

    private static void requireSame(FileStat before, FileStat after) {
        if (!(before instanceof NanosecondFileStat first) || !(after instanceof NanosecondFileStat second)) {
            throw new SecurityException("PROTECTED_CONFIGURATION_NATIVE_TIME_REQUIRED");
        }
        if (before.dev() != after.dev() || before.ino() != after.ino() || before.uid() != after.uid()
                || before.gid() != after.gid() || before.mode() != after.mode() || before.nlink() != after.nlink()
                || before.st_size() != after.st_size() || before.mtime() != after.mtime() || before.ctime() != after.ctime()
                || first.mTimeNanoSecs() != second.mTimeNanoSecs() || first.cTimeNanoSecs() != second.cTimeNanoSecs()) {
            throw new SecurityException("PROTECTED_CONFIGURATION_INPUT_CHANGED");
        }
    }

    private FileStat attributes(Descriptor descriptor) throws IOException {
        var value = posix.allocateStat();
        if (posix.fstat(descriptor.value, value) != 0) throw nativeFailure("PROTECTED_CONFIGURATION_FILE_STAT_FAILED");
        return value;
    }

    private Descriptor descriptor(int value) throws IOException {
        if (value < 0) {
            if (posix.errno() == Errno.ELOOP.intValue()) throw new SecurityException("PROTECTED_CONFIGURATION_SYMLINK_REJECTED");
            throw nativeFailure("PROTECTED_CONFIGURATION_OPEN_FAILED");
        }
        return new Descriptor(value);
    }

    private IOException nativeFailure(String code) { return new IOException(code + ":" + posix.errno()); }

    private static int flags(OpenFlags... values) {
        int result = 0;
        for (var value : values) {
            if (!value.defined()) throw new SecurityException("PROTECTED_CONFIGURATION_NATIVE_FLAG_REQUIRED");
            result |= value.intValue();
        }
        return result;
    }

    public interface OpenAt { @SaveError int openat(int directory, String name, int flags, int mode); }

    private final class Descriptor implements AutoCloseable {
        private final int value;
        private Descriptor(int value) { this.value = value; }
        @Override public void close() throws IOException {
            if (posix.close(value) != 0) throw nativeFailure("PROTECTED_CONFIGURATION_CLOSE_FAILED");
        }
    }
}
