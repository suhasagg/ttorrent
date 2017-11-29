package com.turn.ttorrent.client.network.keyProcessors;

import com.turn.ttorrent.client.network.ReadWriteAttachment;
import com.turn.ttorrent.client.network.WriteTask;
import com.turn.ttorrent.common.LoggerUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.EOFException;
import java.io.IOException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

public class WritableKeyProcessor implements KeyProcessor {

  private static final Logger logger = LoggerFactory.getLogger(WritableKeyProcessor.class);

  @Override
  public void process(SelectionKey key) throws IOException {
    SelectableChannel channel = key.channel();
    if (!(channel instanceof SocketChannel)) {
      logger.warn("incorrect instance of channel. The key is cancelled");
      key.cancel();
      return;
    }

    SocketChannel socketChannel = (SocketChannel) channel;

    Object attachment = key.attachment();
    if (!(attachment instanceof ReadWriteAttachment)) {
      logger.error("incorrect instance of attachment for channel {}", channel);
      key.cancel();
      return;
    }

    ReadWriteAttachment keyAttachment = (ReadWriteAttachment) attachment;

    if (keyAttachment.getWriteTasks().isEmpty()) {
      key.interestOps(SelectionKey.OP_READ);
      return;
    }

    WriteTask processedTask = keyAttachment.getWriteTasks().peek();

    if (!processedTask.getByteBuffer().hasRemaining()) {
      keyAttachment.getWriteTasks().remove();
      return;
    }

    try {
      int writeCount = socketChannel.write(processedTask.getByteBuffer());
      if (writeCount < 0) {
        throw new EOFException("Reached end of stream while writing");
      }
    } catch (IOException e) {
      LoggerUtils.errorAndDebugDetails(logger, "unable to write data to channel {}", socketChannel, e);
      processedTask.getListener().onWriteFailed();
      keyAttachment.getWriteTasks().clear();
      key.cancel();
    }
  }

  @Override
  public boolean accept(SelectionKey key) {
    return key.isValid() && key.isWritable();
  }
}
