inf226 Mandatory 3

Task 0, part A,B:

Created two new immutable classes Password and UserName. Using SCrypt, we now store passwords safely using salt.
When trying to register the program will only accept passwords that meet NIST criteria. When logging in the program
will check if the password is infact stored in our database. 

Part C:
Added: response.addHeader("Set-Cookie", "session=" + session.identity.toString() + "; HttpOnly; SameSite=strict");
in Handler.java so that httpOnly flag is set correctly. When logged in the browser will now tell us that flags are
sat correctly.

Task 1:

Made all SQL statements to PreparedStatements in AccountStorage, ChannelStorage, EventStorage, SessionStorage and UserStorage.

Task 2:

Created a method that escapes all html before using the user input. So every user input anywhere on the website will 
first go through the escapeCode method, and what will be returned will not be interpreted as code.
If a user inputs <script>alert("Hello")</script>, an alert box will no longer pop up, but the string 
<script>alert("Hello")</script> will actually be printed out as his message.