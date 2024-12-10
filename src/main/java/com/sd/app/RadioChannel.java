package com.sd.app;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;

import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Channel;

public class RadioChannel
{
    private final Cipher cipher;
    public String name;
    public List<Track> tracks = new ArrayList<Track>();
    public Track playing;
    public long playedTime;

    public RadioChannel( Cipher cipher, String name ) {
        this.cipher = cipher;
        this.name = name;
    }

    public void start( Connection connection )
    {
        // RabbitMQ config
        final String QUEUE_NAME = this.name;

        try (Channel channel = connection.createChannel())
        {        
            channel.queueDeclare(QUEUE_NAME, false, false, false, null);
            for ( Track track : this.tracks )
            {
                this.playedTime = 0;
                try (FileInputStream fis = new FileInputStream(track.file))
                {
                    System.out.println("broadcasting " + track.name);

                    // Set current playing track
                    this.playing = track;

                    // Broadcast track
                    byte[] buffer = new byte[track.calcBps()];
                    int bytesRead;
                    while ((bytesRead = fis.read(buffer)) != -1)
                    {
                        channel.basicPublish("", QUEUE_NAME, null, this.cipher.doFinal(buffer));
                        this.playedTime += 1;
                        System.out.println(QUEUE_NAME + " broadcast " + this.playedTime + " seconds");
                        Thread.sleep(1000);
                    }
                    
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    System.out.println("Interrupted " + e.getMessage());
                } 
                catch ( IllegalBlockSizeException e ) {
                    e.printStackTrace();
                }
                catch ( BadPaddingException e ) {
                    e.printStackTrace();
                }
            }
        }
        catch ( IOException | TimeoutException e )
        {
            System.out.println(e.getMessage());
        }
    }
}
