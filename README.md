# Introduction
In this project I implemented a simple version of the game "Set". 
<br />
A description of the original game can be found here: ![Set Card Game](https://en.wikipedia.org/wiki/Set_(card_game)).

# My Version of the Game
The game contains a deck of 81 cards. Each card contains a drawing with four features (color, number, shape, shading).
<br />
The game starts with 12 drawn cards from the deck that are placed on a 3x4 grid on the table.
<br />
The goal of each player is to find a combination of three cards from the cards on the table that are said to make up a *legal set*.
<br />
A *legal set* is defined as a set of 3 cards, that for each one of the four features — color, number, shape, and shading — the three cards must display that feature as either **all the same** or **all different**.
<br />
In other words, for each feature the three cards must avoid having two cards showing one version of the feature and the remaining card showing a different
version.

The possible values of the features are:
* The color: red, green or purple.
* The number of shapes: 1, 2 or 3.
* The geometry of the shapes: squiggle, diamond or oval.
* The shading of the shapes: solid, partial or empty.

# Game Play
The players play together simultaneously on the table, trying to find a legal set of 3 cards. They do so by placing tokens on the cards, and once they place the third token, they should ask the dealer to check if the set is legal.
<br />
If the set is not legal, the player gets a penalty, freezing his ability of removing or placing his tokens for a specified time period.
<br />
If the set is a legal set, the dealer will discard the cards that form the set from the table, replace them with 3 new cards from the deck and give the successful player one point. In this case the player also gets frozen although for a shorter time period.

To keep the game more interesting and dynamic, and in case no legal sets are currently available on the table, once every minute the dealer collects all the cards from the table, reshuffles the deck and draws them anew.
<br />
The game will continue as long as there is a legal set to be found in the remaining cards (that are either on table or in the deck). When there is no legal set left, the game will end and the player with the most points will be declared as the winner!

## Keys Layout
Each player controls 12 unique keys on the keyboard as follows. The default keys are:

![image](https://user-images.githubusercontent.com/102467192/209538378-47d58166-3603-48be-a0e1-dbf0d4f21822.png)

The keys layout is the same as the table's cards slots (3x4), and each key press dispatches the respective player’s action, which is either to place/remove a token from the card in that slot - if a card is present there.

## Player Types
The game supports 2 player types: *humans* and *bots*.
<br />
The input from the *human* players is taken from the physical keyboard as an input.
<br />
The *bots* are simulated by **threads** that continually produce random key presses.

# The User Interface
![image](https://user-images.githubusercontent.com/102467192/209538955-a66acf71-3fdf-458b-812a-5673ad036a49.png)

# Game Flow Diagram
![image](https://user-images.githubusercontent.com/102467192/209539109-fc6f8a17-f174-436f-aa2c-f18c0d1edcc7.png)

# Configuration File
All the entities in this game are configured to work with the values in the configuration file.
<br />
You are free to play with it and choose your own settings.
