package com.avantica.videochat.main;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;

import com.avantica.videochat.R;
import com.avantica.videochat.call.CallActivity;
import com.avantica.videochat.model.Call;


public class MainActivity extends AppCompatActivity  implements UserListAdapter.ItemClickListener {

    private static final String CALL = "call";

    private MainViewModel viewModel;
    private UserListAdapter userListAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ActionBar bar = getSupportActionBar();
        bar.setTitle(getString(R.string.welcome));

        viewModel = new MainViewModel(getApplicationContext());

        RecyclerView userList = this.findViewById(R.id.usersList);
        userListAdapter = new UserListAdapter(getApplicationContext(), viewModel.users);
        userListAdapter.setClickListener(this);
        userList.setAdapter(userListAdapter);
        LinearLayoutManager manager = new LinearLayoutManager(this);
        userList.setLayoutManager(manager);

        viewModel.onUsername.observe(this, s -> {
            ActionBar bar1 = getSupportActionBar();
            bar1.setTitle(getString(R.string.welcome) + " " + s);
        });

        viewModel.onNoUsername.observe(this, aBoolean -> showNoUsername());

        viewModel.onUsersUpdated.observe(this, users -> userListAdapter.notifyDataSetChanged());

        viewModel.onCallUpdated.observe(this, call -> {
            handleCall(call);
        });

        viewModel.onUserBusy.observe(this, isBusy -> {
            showUserBusy();
        });
    }

    @Override
    public void onItemClick(View view, int position) {
        viewModel.callUser(userListAdapter.getItem(position));
    }

    private void showNoUsername() {
        final EditText taskEditText = new EditText(this);
        taskEditText.setInputType(InputType.TYPE_CLASS_TEXT);
        new AlertDialog.Builder(this)
                .setTitle(R.string.username)
                .setMessage(R.string.write_username)
                .setView(taskEditText)
                .setPositiveButton(R.string.ok, (diaglog, button) -> {
                    String text = String.valueOf(taskEditText.getText());
                    if (text.isEmpty()) {
                        showNoUsername();
                    } else {
                        viewModel.setUsername(text);
                    }
                })
                .create()
                .show();
    }

    private void showUserBusy() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.sorry)
                .setMessage(R.string.busy_message)
                .setPositiveButton(R.string.ok, (dialog, button) -> {})
                .create()
                .show();

    }

    private void handleCall(Call call) {
        Intent callIntent = new Intent(getApplicationContext(), CallActivity.class);
        callIntent.putExtra(CALL, call);
        startActivity(callIntent);
    }
}
