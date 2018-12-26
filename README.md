# Sensor-Gym-App
CSC490 Movesense sensor android application
This application was created for the CSC490 - Sensors and IOT class at SUNY Oswego.
It requires you to use a Movesense sensor on your left wrist.

The application is made for weightlifting, it can recognize whether you're deadlifting, squatting, benching or doing biceps curls
and it will count how many reps you do.

The application uses the pitch and roll of the sensor to determine what position it is in and increments a counter for that movement's 
positioning. At the end of the movement whichever position has the highest counter, that is determined to be the movement your were doing.

To count the reps that you do, it filters the acceleration by using a simple moving average and the standard deviation.
First it will look for a local peak in the array of moving averages, then whenever the peak is above the mean plus the standard deviation
a rep has been found and increments a counter.

If a user has entered what weight they were attempting, it will use the counted reps and the supplied weight value to determine what their
theoretical one rep max would be.

The attached MainActivity.JAVA contains all of my code, the rest was provided in a Sample App from the Movesense site.
