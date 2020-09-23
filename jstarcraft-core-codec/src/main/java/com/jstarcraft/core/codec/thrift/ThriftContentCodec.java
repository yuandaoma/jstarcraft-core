package com.jstarcraft.core.codec.thrift;

import com.jstarcraft.core.codec.ContentCodec;
import com.jstarcraft.core.codec.exception.CodecException;
import com.jstarcraft.core.codec.specification.ClassDefinition;
import com.jstarcraft.core.codec.specification.CodecDefinition;
import com.jstarcraft.core.codec.thrift.converter.ProtocolConverter;
import com.jstarcraft.core.common.reflection.Specification;
import com.jstarcraft.core.common.reflection.TypeUtility;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TIOStreamTransport;
import org.apache.thrift.transport.TTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.reflect.Type;
import java.util.function.Function;

/**
 * @author huang hong fei
 * @createAt 2020/9/11
 * @description thrift格式编码解码器
 */
public class ThriftContentCodec implements ContentCodec {

    private static final Logger log= LoggerFactory.getLogger(ThriftContentCodec.class);

    private CodecDefinition codecDefinition;

    private Function<TTransport,TProtocol> protocolFactory;

    public ThriftContentCodec(CodecDefinition codecDefinition){
        this(codecDefinition,TBinaryProtocol::new);
    }

    public ThriftContentCodec(CodecDefinition codecDefinition, Function<TTransport,TProtocol> factory){
        this.codecDefinition=codecDefinition;
        this.protocolFactory=factory;
    }

    @Override
    public Object decode(Type type, byte[] content) {
        if(content==null||content.length==0){
            return null;
        }
        try {
            return decode(type,nullInputStream());
        } catch (Exception exception) {
            String message = "Thrift解码失败:" + exception.getMessage();
            log.error(message, exception);
            throw new CodecException(message, exception);
        }
    }

    @Override
    public Object decode(Type type, InputStream stream) {
        try {
            if(stream.available()==0){
                return null;
            }
            TIOStreamTransport transport = new TIOStreamTransport(stream);
            ThriftReader context = new ThriftReader(codecDefinition,protocolFactory.apply(transport));
            ProtocolConverter converter = context.getProtocolConverter(Specification.getSpecification(type));
            ClassDefinition classDefinition = codecDefinition.getClassDefinition(TypeUtility.getRawType(type, null));
            return converter.readValueFrom(context, type, classDefinition);
        } catch (Exception exception) {
            String message = "Thrift解码失败:" + exception.getMessage();
            log.error(message, exception);
            throw new CodecException(message, exception);
        }
    }

    @Override
    public byte[] encode(Type type, Object content) {
        if(content==null){
            return new byte[]{};
        }
        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(); DataOutputStream dataOutputStream = new DataOutputStream(byteArrayOutputStream)) {
            encode(type, content, dataOutputStream);
            return byteArrayOutputStream.toByteArray();
        } catch (Exception exception) {
            String message = "Thrift编码失败:" + exception.getMessage();
            log.error(message, exception);
            throw new CodecException(message, exception);
        }
    }

    @Override
    public void encode(Type type, Object content, OutputStream stream) {
        if(content==null){
            return;
        }
        try {
            TIOStreamTransport transport = new TIOStreamTransport(stream);
            ThriftWriter context = new ThriftWriter(codecDefinition,protocolFactory.apply(transport));
            ProtocolConverter converter = context.getProtocolConverter(Specification.getSpecification(type));
            ClassDefinition classDefinition = codecDefinition.getClassDefinition(TypeUtility.getRawType(type, null));
            converter.writeValueTo(context, type, classDefinition, content);
        } catch (Exception exception) {
            String message = "Thrift编码失败:" + exception.getMessage();
            log.error(message, exception);
            throw new CodecException(message, exception);
        }
    }

    /**
     * 构建一个空的InputStream对象
     * @return
     */
    public static InputStream nullInputStream() {
        return new InputStream() {
            @Override
            public int read() throws IOException {
                return 0;
            }
        };
    }

}
