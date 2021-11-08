inf226 Mandatory 3

Task 0, part A,B:

Created two new immutable classes Password and UserName. Using SCrypt, we now store passwords safely using salt.
When trying to register the program will only accept passwords that meet NIST criteria. When logging in the program
will check if the password is infact stored in our database. 

Part C:
Added: response.addHeader("Set-Cookie", "session=" + session.identity.toString() + "; HttpOnly; SameSite=strict");
in Handler.java so that httpOnly flag is set correctly. When logged in the browser will now tell us that flags are
sat correctly.


Task 2:

Created a method that escapes all html before using the user input. So every user input anywhere on the website will 
first go through the escapeCode method, and what will be returned will not be interpreted as code.
If a user inputs <script>alert("Hello")</script>, an alert box will no longer pop up, but the string 
<script>alert("Hello")</script> will actually be printed out as his message.

Task 4:

Created enum class Role that holds all types of roles. Added many new methods in InChat that checks if a user has
permission to perform certain tasks (isOwner, isBanned, canPost etc.). 

Using these methods within the already implemented methods of InChat, we implement functional access control. A owner can now delete all messages, but a
participant can only delete his own messages and noone elses. An observer can only observe, and not post messages.
A person that creates a channel will automatically be given Owner role, and a user that joins a channel will 
automatically will be given Participant role. A banned user will not be able to perform events in the channel anymore,
and the channel will be hidden from their channel list.

We also added functionality to the dropdown menu for roles, this was fixed in the Handler class.