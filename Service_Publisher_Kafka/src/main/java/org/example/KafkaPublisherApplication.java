package org.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Random;
import java.util.Scanner;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@SpringBootApplication
public class KafkaPublisherApplication implements CommandLineRunner {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private ScheduledFuture<?> currentTask;
    private final AtomicInteger messagesPerMinute = new AtomicInteger(100);
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final Random random = new Random();

    public KafkaPublisherApplication(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public static void main(String[] args) {
        SpringApplication.run(KafkaPublisherApplication.class, args);
    }

    @Override
    public void run(String... args) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("Введите команды: start | stop | setLimit <0-1000000>");

        while (true) {
            System.out.print(">>> ");
            String input = scanner.nextLine().trim();

            if (input.equalsIgnoreCase("start")) {
                startSending();
            } else if (input.equalsIgnoreCase("stop")) {
                stopSending();
            } else if (input.toLowerCase().startsWith("setlimit")) {
                handleSetLimit(input);
            } else {
                System.out.println("Неизвестная команда.");
            }
        }
    }

    private void handleSetLimit(String input) {
        String[] parts = input.split("\\s+");
        if (parts.length == 2) {
            try {
                int newLimit = Integer.parseInt(parts[1]);
                if (newLimit < 0 || newLimit > 1_000_000) {
                    System.out.println("Значение должно быть от 0 до 1000000");
                } else {
                    messagesPerMinute.set(newLimit);
                    System.out.println("Установлен лимит: " + newLimit + " сообщений в минуту");
                    if (isRunning.get()) {
                        restartSending();
                    }
                }
            } catch (NumberFormatException e) {
                System.out.println("Неверный формат числа.");
            }
        } else {
            System.out.println("Формат команды: setLimit <число>");
        }
    }

    private void startSending() {
        if (isRunning.compareAndSet(false, true)) {
            scheduleMessages();
            System.out.println("Старт отправки сообщений.");
        } else {
            System.out.println("Уже работает.");
        }
    }

    private void stopSending() {
        if (isRunning.compareAndSet(true, false)) {
            if (currentTask != null) {
                currentTask.cancel(false);
            }
            System.out.println("Остановка отправки сообщений.");
        } else {
            System.out.println("Уже остановлено.");
        }
    }

    private void restartSending() {
        if (currentTask != null) {
            currentTask.cancel(false);
        }
        scheduleMessages();
    }

    private void scheduleMessages() {
        int rate = messagesPerMinute.get();
        long intervalMillis = (rate > 0) ? Math.max((long) (60_000.0 / rate), 1) : Long.MAX_VALUE;

        Runnable task = () -> {
            if (isRunning.get()) {
                try {
                    String value = String.format("%.2f", random.nextDouble() * 100);
                    ParamMessage message = new ParamMessage(value);
                    String json = objectMapper.writeValueAsString(message);

                    kafkaTemplate.send("param1", json);
                    kafkaTemplate.send("param2", json);
                    kafkaTemplate.send("param3", json);
                } catch (Exception e) {
                    System.err.println("Ошибка отправки в Kafka: " + e.getMessage());
                }
            }
        };

        currentTask = scheduler.scheduleAtFixedRate(task, 0, intervalMillis, TimeUnit.MILLISECONDS);
    }
}
