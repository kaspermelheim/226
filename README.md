inf226 Mandatory 3

Task 0, part A,B:

Created two new immutable classes Password and UserName. Using SCrypt, we now store passwords safely using salt.
When trying to register the program will only accept passwords that meet NIST criteria. When logging in the program
will check if the password is infact stored in our database. 

Part C:
Added in Handler.handle():

Cookie sc = new Cookie("session",session.identity.toString());
sc.setSecure(true);
response.addCookie(sc);
response.addHeader("Set-Cookie", "key=value; HttpOnly; SameSite=strict");

When logged in the browser will now tell us that flags are sat correctly.


Task 2:

Created a method that escapes all html before using the user input. So every user input anywhere on the website will 
first go through the escapeCode method, and what will be returned will not be interpreted as code.
If a user now tries to input a script that runs an alert box, the alert box will no longer appear. Instead, the actual
code will be printed as String as a message, and not ran as code on the website.

Task 4:

Created enum class Role that holds all types of roles. Added many new methods in InChat that checks if a user has
permission to perform certain tasks (isOwner, isBanned, canPost etc.). 

Using these methods within the already implemented methods of InChat, we implement functional access control. 
An owner or moderator can now delete and edit all messages, but a participant can only delete his own messages 
and noone elses. 

An observer can only observe, and not post messages. A person that creates a channel will automatically be given 
Owner role, and a user that joins a channel will automatically will be given Participant role. 

A banned user will not be able to perform events in the channel anymore, and the channel will be hidden from 
their channel list.

We also added functionality to the dropdown menu for roles, this was fixed in the Handler class.

Task 5:

We noticed that a user could register even if he typed in two different passwords in the registration form.
This has now been fixed and a user can only register if the two passwords are correct.

The colors and design that was there already can be harmful to people interested in design or with severe OCD, so we
also made some changes to that to improve the experience for them.