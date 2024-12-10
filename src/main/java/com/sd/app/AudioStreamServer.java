package com.sd.app;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.sound.sampled.UnsupportedAudioFileException;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import java.io.File;
import java.io.IOException;
import java.rmi.RemoteException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Security;

public class AudioStreamServer
{
    private final String SECRET_KEY = "RG0LGLuBMmDlPSJn+1OCag==";
    private LinkedHashMap<String, RadioChannel> radioChannels = new LinkedHashMap<String, RadioChannel>();

    public AudioStreamServer() throws RemoteException, IOException, UnsupportedAudioFileException, TimeoutException, InvalidKeyException, NoSuchAlgorithmException, NoSuchProviderException, NoSuchPaddingException
    {
        // Adicionar o provedor Bouncy Castle
        Security.addProvider(new BouncyCastleProvider());

        SecretKey secretKey = new SecretKeySpec(Base64.getDecoder().decode(SECRET_KEY), "IDEA");

        // Inicializar o Cipher para criptografia
        Cipher cipher = Cipher.getInstance("IDEA/ECB/PKCS5Padding", "BC");
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            
        // Pop
        RadioChannel pop = new RadioChannel(cipher, "POP");
        pop.tracks.add(new Track("Martin Garrix - Smile", new File("src/main/resources/martingarrix_smile.wav"), 78));
        this.radioChannels.put(pop.name, pop);
        
        // Eletro
        RadioChannel eletro = new RadioChannel(cipher, "ELETRO");
        eletro.tracks.add(new Track("Vintage Culture - Deep Inside", new File("src/main/resources/vintage_culture_deep_inside.wav"), 262));
        this.radioChannels.put(eletro.name, eletro);

        // Sertanejo
        RadioChannel sertanejo = new RadioChannel(cipher, "SERTANEJO");
        sertanejo.tracks.add(new Track("Gusttavo Lima - Morar Nesse Motel", new File("src/main/resources/GUSTTAVO_LIMA_MORAR_NESSE_MOTEL.wav"), 238));
        this.radioChannels.put(sertanejo.name, sertanejo);

        ExecutorService executorService = Executors.newFixedThreadPool(this.radioChannels.size());

        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        factory.setPort(5672);
        Connection connection = factory.newConnection();

        // Create threads for each radio channel
        for ( String channel : this.radioChannels.keySet() )
        {
            executorService.execute( () ->
            {
                // Start channel
                this.radioChannels.get(channel).start( connection );
            });
        }

        // Broadcast via RabbitMQ
        this.broadcastChannels( connection );

        // Wait for channels shutdown
        executorService.shutdown();

        // Close RabbitMQ
        connection.close();

    }

    public void broadcastChannels(Connection connection)
    {
        // RabbitMQ config
        final String QUEUE_NAME = "CHANNEL_LIST";

        try (Channel channel = connection.createChannel())
        {        
            channel.queueDeclare(QUEUE_NAME, false, false, false, null);

            while ( true )
            {
                String message = String.join(";", new ArrayList<>(this.radioChannels.keySet()));
                System.out.println("CHANNEL_LIST BROADCASTED");
                channel.basicPublish("", QUEUE_NAME, null, message.getBytes());

                try
                {
                    Thread.sleep(1000);
                }
                catch ( InterruptedException e )
                {
                    System.out.println(e.getMessage());
                    break;
                }
            }

        }
        catch ( IOException | TimeoutException e )
        {
            System.out.println(e.getMessage());
        }
    }

    public List<String> getRadioChannels()
    {
        return new ArrayList<>(this.radioChannels.keySet());
    }
}
