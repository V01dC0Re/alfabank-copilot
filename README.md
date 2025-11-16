# CopilotApp — AI-ассистент для малого бизнеса

Веб-приложение на Java EE / Jakarta EE с интеграцией LLM через Hugging Face, помогающее предпринимателям в рутинных задачах: генерация текстов, анализ данных, ответы на вопросы на русском языке.

## Стек
- **Сервер приложений**: WildFly 38 (Jakarta EE 9)
- **База данных**: PostgreSQL 42
- **Backend**: Java 17, CDI (`@SessionScoped`, `@ApplicationScoped`), JPA/Hibernate
- **Frontend**: JSF 2.3 + PrimeFaces 12
- **LLM**: Hugging Face Inference API (`Qwen/Qwen2.5-7B-Instruct`)

## Основные возможности
- Регистрация и вход
- Сохраняемые чаты (пользовательские сессии)
- Отправка промптов → ответы от модели → сохранение в БД
- Повтор запросов при сетевых ошибках (до 2х попыток)

## Настройка окружения

### 1. PostgreSQL
Создайте БД и пользователя:

sudo apt install postgresql postgresql-contrib -y

sudo -u postgres psql

Поключение к БД от имени системного пользователя 

CREATE DATABASE db;<br>
CREATE USER ваше_имя WITH PASSWORD 'ваш_пароль';

Создание БД, установка пароля

Установка JDBC-драйвера PostgreSQL в WildFly<br>
Скачиваем драйвер<br>
cd /tmp<br>
wget https://jdbc.postgresql.org/download/postgresql-42.7.3.jar

Создаем модуль wildfly<br>
sudo mkdir -p /opt/wildfly/modules/org/postgresql/main<br>
sudo cp postgresql-42.7.3.jar /opt/wildfly/modules/org/postgresql/main/

Создаем module.xml 
sudo nano /opt/wildfly/modules/org/postgresql/main/module.xml

Содержимое
```
<?xml version="1.0" encoding="UTF-8"?>
<module xmlns="urn:jboss:module:1.3" name="org.postgresql">
    <resources>
        <resource-root path="postgresql-42.7.3.jar"/>
    </resources>
    <dependencies>
        <module name="javax.api"/>
        <module name="javax.transaction.api"/>
    </dependencies>
</module>
```

### 2. WildFly DataSource

Отредактировали файл /opt/wildfly/standalone/configuration/standalone.xml

В секцию datasources добавили:
```
<datasource jndi-name="java:jboss/datasources/MyAppDB"
            pool-name="MyAppDB"
            enabled="true"
            use-java-context="true">
    <connection-url>jdbc:postgresql://localhost:5432/db</connection-url>
    <driver>postgresql</driver>
    <security>
        <user-name>ivagroz</user-name>
        <password>123456789Lw!</password>
    </security>
</datasource>
```
В секцию drivers добавили:
```
<driver name="postgresql" module="org.postgresql">
    <xa-datasource-class>org.postgresql.xa.PGXADataSource</xa-datasource-class>
</driver>
```

Эти файлы хранятся в архиве standalone.zip


### 3. Hugging Face API Token
На сайте https://huggingface.co/settings/tokens получаем токен для подключения
Используем модель Qwen/Qwen2.5-7B-Instruct, так как нет возможности развернуть и обучить свою модель локально/на сервере. В случае дальнейшего развития проекта и имея должные мощности "железа" будет развернута своя модель

## 📁 Структура
```
.
└── main
    ├── java
    │   ├── beans
    │   │   ├── ChatBean.java
    │   │   └── LoginBean.java
    │   ├── entities
    │   │   ├── Chat.java
    │   │   ├── Message.java
    │   │   └── User.java
    │   └── services
    │       └── HuggingFaceService.java
    ├── resources
    │   └── META-INF
    │       └── persistence.xml
    └── webapp
        ├── css
        │   ├── login-style.css
        │   ├── main-style.css
        │   └── start-style.css
        ├── js
        │   └── theme.js
        ├── login.xhtml
        ├── main.xhtml
        ├── register.xhtml
        ├── start.xhtml
        └── WEB-INF
            └── web.xml
```

## Запуск
1. Соберите проект:  
   ```bash
   mvn clean package
   ```
2. Разверните `.war` в WildFly:  
   ```bash
   cp target/copilotapp.war $WILDFLY/standalone/deployments/
   ```
3. Запустите WildFly:  
   ```bash
   /opt/wildfly/bin/standalone.sh -b 0.0.0.0 -bmanagement 0.0.0.0
   ```

## Примечание
Файл для деплоя - ROOT.war - также прикреплен. WildFly скачивалось с официального сайта последней версии


Над проектом работали:
Чупров Иван, @V01dC0Re<br>
Гладышев Иван @IvaGroz<br>
Четырина Мария, @Feel69Good<br>
