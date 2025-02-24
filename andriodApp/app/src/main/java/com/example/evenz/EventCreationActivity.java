package com.example.evenz;

import static java.util.UUID.randomUUID;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.UnstableApi;

import android.annotation.SuppressLint;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.View;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.IOException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import java.text.ParseException;
import java.util.Objects;
import java.util.UUID;
import java.util.zip.CRC32C;

/**
 * Java Class behind the EventCreation activity, providing the organizer with a UI
 * to create events, and save it to Firebase. Extends AppCompactACtivity and DatePickerDialog.
 * contains fields storing data associated with the Event, initalized through the process.
 */
@UnstableApi public class EventCreationActivity extends AppCompatActivity implements DatePickerDialog.OnDateSetListener {

    // Define constants at the top of your class
    private static final int PICK_IMAGE_REQUEST = 22;
    private Uri filePathPoster, filePathQR;
    StorageReference storageReference;
    StorageReference photoRef, qrRef;

    private ImageView datePickerButton, uploadPosterImg, uploadQRImg;
    private EditText editTextOrganizerName,editTextEventName, editDate, editTextAttendeeLimit, editTextEventInfo, editTextEventLoc;
    private RelativeLayout submitEventButton;

    private String eventPosterID_temp, eventQrID, uploadedImg;
    private String eventID;
    private Bitmap qrBitmap;
    // ImageUtility instance
    private ImageUtility imageUtility;

    /**
     * initalizes firebase storage reference, information, and calls other initalizer functions like initUI(),
     * and connects to create_event.xml UI file.
     * and provides the code for UI element interaction, setting the code for
     * sepecifying a date,
     * uploading poster image,
     *QR image, and the submit button,
     * @param savedInstanceState If the activity is being re-initialized after
     *     previously being shut down then this Bundle contains the data it most
     *     recently supplied in {@link #onSaveInstanceState}.  <b><i>Note: Otherwise it is null.</i></b>
     *
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.create_event);

        // Initialize Firebase Storage reference only once
        storageReference = FirebaseStorage.getInstance().getReference();

        // Initialize ImageUtility
        imageUtility = new ImageUtility();

        // Get extras from intent
        Bundle b = getIntent().getExtras();
        assert b != null;

        eventPosterID_temp = randomUUID().toString();
        photoRef = storageReference.child("images/" + eventPosterID_temp);

        // Initialize UI components
        initUI();

        datePickerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                DatePickerFragment mDatePickerDialogFragment = new DatePickerFragment();
                mDatePickerDialogFragment.show(getSupportFragmentManager(), "DATE PICK");
            }
        });

        uploadPosterImg.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                uploadedImg = "poster";
                select();
            }
        });

        uploadQRImg.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                uploadedImg = "qr";
                select();
            }
        });

        // Submit event button listener for initiating the upload process
        submitEventButton.setOnClickListener(view -> {
            if (filePathPoster != null) {
                ImageUtility.UploadCallback callback = new ImageUtility.UploadCallback() {
                    @Override
                    public void onSuccess(String imageID, String imageURL) {
                        // Image successfully uploaded
                        // Proceed with event submission or other actions as needed
                        eventPosterID_temp = imageID; // If needed for further operations
                        try {
                            submitEvent();
                        } catch (ParseException e) {
                            e.printStackTrace();
                        }
                        submitOrganizer();
                        navigateToHomeScreen();
                    }

                    @Override
                    public void onFailure(Exception e) {
                        // Handle upload failure
                        Toast.makeText(EventCreationActivity.this, "Upload failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                };

                imageUtility.upload(filePathPoster, callback);
            } else {
                Toast.makeText(EventCreationActivity.this, "Please select an image first", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * initates te image selection
     */
    private void select() {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(Intent.createChooser(intent, "Select Image from here..."), PICK_IMAGE_REQUEST);
    }

    /**
     *sets the selected date.
     * @param view the picker associated with the dialog
     * @param year the selected year
     * @param month the selected month (0-11 for compatibility with
     *              {@link Calendar#MONTH})
     * @param dayOfMonth the selected day of the month (1-31, depending on
     *                   month)
     */
    @Override
    public void onDateSet(DatePicker view, int year, int month, int dayOfMonth) {
        Calendar mCalendar = Calendar.getInstance();
        mCalendar.set(Calendar.YEAR, year);
        mCalendar.set(Calendar.MONTH, month);
        mCalendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
        String selectedDate = DateFormat.getDateInstance(DateFormat.FULL).format(mCalendar.getTime());
        editDate.setText(selectedDate);
    }


    /**
     * Function that stores the image and QR code bitmaps
     * @param requestCode The integer request code originally supplied to
     *                    startActivityForResult(), allowing you to identify who this
     *                    result came from.
     * @param resultCode The integer result code returned by the child activity
     *                   through its setResult().
     * @param data An Intent, which can return result data to the caller
     *               (various data can be attached to Intent "extras").
     *
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            try {
                Bitmap bitmap;
                if (Objects.equals(uploadedImg, "poster")) {
                    filePathPoster = data.getData();
                    bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), filePathPoster);
                    uploadPosterImg.setImageBitmap(bitmap);
                }
                else {
                    filePathQR = data.getData();
                    bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), filePathQR);
                    qrBitmap = bitmap;
                    uploadQRImg.setImageBitmap(bitmap);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * initalizes all the Views in the EventCreationActivity.
     */
    private void initUI() {
        editTextOrganizerName = findViewById(R.id.editTextOrganizerName);
        editTextEventName = findViewById(R.id.editTextEventName);
        editDate = findViewById(R.id.editDate); // Date picker
        datePickerButton = findViewById(R.id.event_Date_Picker);
        editTextAttendeeLimit = findViewById(R.id.no_limit);
        editTextEventInfo = findViewById(R.id.editTextEventInfo);
        editTextEventLoc = findViewById(R.id.editTextLocation);
        uploadPosterImg = findViewById(R.id.upload_poster_img);
        uploadQRImg = findViewById(R.id.upload_qr_img);
        submitEventButton = findViewById(R.id.create_event_button); //Create event button
    }

    /**
     * returns to homescreen via startactivity(intent)
     */
    private void navigateToHomeScreen() {
        Intent intent = new Intent(EventCreationActivity.this, HomeScreenActivity.class);
        Bundle b = new Bundle();
        b.putString("role", "organizer");
        b.putString("eventID", eventID);
        intent.putExtras(b);
        startActivity(intent);
    }

    /**
     * Function called upon pressing submit button
     * saves the data currently entered in editText Views, initalized firebase instance, stores data into a hashmap,
     * and uploads it.
     * @throws ParseException
     */
    private void submitEvent() throws ParseException {
        String eventName = editTextEventName.getText().toString().trim();
        String eventPosterID = eventPosterID_temp; // Assuming a default or gathered elsewhere
        String description = editTextEventInfo.getText().toString().trim();
        String attendeeLimit = editTextAttendeeLimit.getText().toString().trim();
        String orgName = editTextOrganizerName.getText().toString().trim();
        String eventDatestring = editDate.getText().toString().trim();
        String location = editTextEventLoc.getText().toString().trim();

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        DocumentReference newEventRef = db.collection("events").document();

        eventID = newEventRef.getId(); // Use this eventID for your operations

        DateFormat dateFormat = DateFormat.getDateInstance(DateFormat.FULL, Locale.getDefault());
        Date eventDate = dateFormat.parse(eventDatestring);

        if(qrBitmap != null) {
            eventID = imageUtility.decodeQRCode(qrBitmap);
        }

        // Prepare the event data map
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("eventName", eventName);
        eventData.put("description", description);
        eventData.put("eventPosterID", eventPosterID);
        eventData.put("eventAttendLimit", Integer.parseInt(attendeeLimit));
        eventData.put("organizationName", orgName);
        eventData.put("eventDate", eventDate);
        eventData.put("location", location);
        eventData.put("eventID", eventID);


        EventUtility.storeEventwnm(eventData, eventID);
        // Update the user's document with the new event ID (if necessary)
        submitOrganizer();
        // Navigate to the home screen after the event has been successfully stored
        navigateToHomeScreen(eventID);
    }


    /**
     * submits organizer to the User collection.
     * adds to the organizer the event that they
     * just created to the list of events
     */
    private void submitOrganizer() {
        @SuppressLint("HardwareIds") String deviceID = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        String name = editTextOrganizerName.getText().toString().trim();
        // Create a new user with the feilds and all other set to null
        User organizer = new User(deviceID, name, "", "", "", "Organizer", false, false);

        // check if this is organizers first event
        FirebaseUserManager firebaseUserManager = new FirebaseUserManager();
        firebaseUserManager.getUserExist(deviceID).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                firebaseUserManager.addEventToUser(deviceID, eventID).addOnCompleteListener(task1 -> {
                    if (task1.isSuccessful()) {
                        // event added just navigate to home
                        Log.d("EventCreationActivity", "Organizer added successfully");
                        navigateToHomeScreen();
                    }
                });
            }
            else {
                firebaseUserManager.submitUser(organizer).addOnCompleteListener(task1 -> {
                    if (task1.isSuccessful()) {
                        // Add the event to the organizer's event list
                        firebaseUserManager.addEventToUser(deviceID, eventID).addOnCompleteListener(task2 -> {
                            if (task2.isSuccessful()) {
                                Log.d("EventCreationActivity", "Organizer added successfully");
                                navigateToHomeScreen();
                            } else {
                                Log.e("EventCreationActivity", "Failed to add event to organizer", task2.getException());

                            }
                        });
                    } else {
                        Log.e("EventCreationActivity", "Failed to submit organizer", task1.getException());
                    }
                });
            }
        });
    }

    /**
     * navigates to the homescreen of the event given
     * @param eventID event id of event to navigate to
     */
    private void navigateToHomeScreen(String eventID) {
        Intent intent = new Intent(EventCreationActivity.this, HomeScreenActivity.class);
        intent.putExtra("role", "organizer");
        intent.putExtra("eventID", eventID);
        startActivity(intent);
    }



}
