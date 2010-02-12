package com.mbien.opencl;

import com.sun.gluegen.runtime.BufferFactory;
import com.sun.gluegen.runtime.PointerBuffer;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

import static com.mbien.opencl.CLException.*;
import static com.mbien.opencl.CL.*;

/**
 *
 * @author Michael Bien
 */
public abstract class CLMemory <B extends Buffer> implements CLResource {
    
    B buffer;
    
    public final long ID;

    protected final CLContext context;
    protected final CL cl;

    protected <Buffer> CLMemory(CLContext context, long id) {
        this.context = context;
        this.cl = context.cl;
        this.ID = id;
    }
    
    protected CLMemory(CLContext context, B directBuffer, long id) {
        this.buffer = directBuffer;
        this.context = context;
        this.cl = context.cl;
        this.ID = id;
    }

    protected static final boolean isHostPointerFlag(int flags) {
        return (flags & CL_MEM_COPY_HOST_PTR) != 0 || (flags & CL_MEM_USE_HOST_PTR) != 0;
    }

    protected static final int sizeOfBufferElem(Buffer buffer) {
        if (buffer instanceof ByteBuffer) {
            return BufferFactory.SIZEOF_BYTE;
        } else if (buffer instanceof IntBuffer) {
            return BufferFactory.SIZEOF_INT;
        } else if (buffer instanceof ShortBuffer) {
            return BufferFactory.SIZEOF_SHORT;
        } else if (buffer instanceof FloatBuffer) {
            return BufferFactory.SIZEOF_FLOAT;
        } else if (buffer instanceof DoubleBuffer) {
            return BufferFactory.SIZEOF_DOUBLE;
        }
        throw new RuntimeException("Unexpected buffer type " + buffer.getClass().getName());
    }

    /**
     * Returns a new instance of CLMemory pointing to the same CLResource but using a different Buffer.
     */
    public abstract <T extends Buffer> CLMemory<T> cloneWith(T directBuffer);


    public CLMemory<B> use(B buffer) {
        if(this.buffer != null && buffer != null && this.buffer.getClass() != buffer.getClass()) {
            throw new IllegalArgumentException(
                    "expected a Buffer of class " + this.buffer.getClass()
                    +" but got " + buffer.getClass());
        }
        this.buffer = buffer;
        return this;
    }

    public B getBuffer() {
        return buffer;
    }

    /**
     * Returns the size of the wrapped direct buffer in byte.
     */
    public int getSize() {
        if(buffer == null) {
            return 0;
        }
        return sizeOfBufferElem(buffer) * buffer.capacity();
    }

    public long getCLSize() {
        PointerBuffer pb = PointerBuffer.allocateDirect(1);
        int ret = cl.clGetMemObjectInfo(ID, CL_MEM_SIZE, PointerBuffer.elementSize(), pb.getBuffer(), null);
        checkForError(ret, "can not optain buffer info");
        return pb.get();
    }

    public void release() {
        int ret = cl.clReleaseMemObject(ID);
        context.onMemoryReleased(this);
        checkForError(ret, "can not release mem object");
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final CLMemory<?> other = (CLMemory<?>) obj;
        if (this.ID != other.ID) {
            return false;
        }
        if (this.context != other.context && (this.context == null || !this.context.equals(other.context))) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 83 * hash + (int) (this.ID ^ (this.ID >>> 32));
        hash = 83 * hash + (this.context != null ? this.context.hashCode() : 0);
        return hash;
    }

    @Override
    public String toString() {
        return "CLMemory [id: " + ID+"]";
    }

    /**
     * Memory settings for configuring CLMemory.
     */
    public enum Mem {

        /**
         * Enum representing CL_MEM_READ_WRITE.
         * This flag specifies that the memory object will be read and
         * written by a kernel.
         */
        READ_WRITE(CL_MEM_READ_WRITE),

        /**
         * Enum representing CL_MEM_WRITE_ONLY.
         * This flags specifies that the memory object will be written
         * but not read by a kernel.
         * Reading from a buffer or image object created with WRITE_ONLY
         * inside a kernel is undefined.
         */
        WRITE_ONLY(CL_MEM_WRITE_ONLY),

        /**
         * Enum representing CL_MEM_READ_ONLY.
         * This flag specifies that the memory object is a read-only memory
         * object when used inside a kernel. Writing to a buffer or image object
         * created withREAD_ONLY inside a kernel is undefined.
         */
        READ_ONLY(CL_MEM_READ_ONLY),

        /**
         * Enum representing CL_MEM_USE_HOST_PTR.
         * If specified, it indicates that the application wants the OpenCL
         * implementation to use memory referenced by host_ptr as the storage
         * bits for the memory object. OpenCL implementations are allowed
         * to cache the buffer contents pointed to by host_ptr in device memory.
         * This cached copy can be used when kernels are executed on a device.
         */
        USE_BUFFER(CL_MEM_USE_HOST_PTR),

        /**
         * Enum representing CL_MEM_ALLOC_HOST_PTR.
         * This flag specifies that the application wants the OpenCL implementation
         * to allocate memory from host accessible memory.
         * {@link #ALLOC_HOST_PTR} and {@link #USE_BUFFER} are mutually exclusive.
         */
        ALLOC_HOST_PTR(CL_MEM_ALLOC_HOST_PTR),

        /**
         * Enum representing CL_MEM_COPY_HOST_PTR.
         * If {@link #COPY_BUFFER} specified, it indicates that the application
         * wants the OpenCL implementation to allocate memory for the memory object
         * and copy the data from memory referenced by host_ptr.<br/>
         * {@link #COPY_BUFFER} and {@link #USE_BUFFER} are mutually exclusive.
         */
        COPY_BUFFER(CL_MEM_COPY_HOST_PTR);

        /**
         * Value of wrapped OpenCL flag.
         */
        public final int CONFIG;

        private Mem(int config) {
            this.CONFIG = config;
        }

        public static Mem valueOf(int bufferFlag) {
            switch (bufferFlag) {
                case CL_MEM_READ_WRITE:
                    return Mem.READ_WRITE;
                case CL_MEM_READ_ONLY:
                    return Mem.READ_ONLY;
                case CL_MEM_USE_HOST_PTR:
                    return Mem.USE_BUFFER;
                case(CL_MEM_ALLOC_HOST_PTR):
                    return ALLOC_HOST_PTR;
                case CL_MEM_COPY_HOST_PTR:
                    return Mem.COPY_BUFFER;
            }
            return null;
        }

        static int flagsToInt(Mem[] flags) {
            int clFlags = 0;
            if (flags != null) {
                for (int i = 0; i < flags.length; i++) {
                    clFlags |= flags[i].CONFIG;
                }
            }
            if (clFlags == 0) {
                clFlags = CL_MEM_READ_WRITE;
            }
            return clFlags;
        }
    }

   

}