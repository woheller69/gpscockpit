package org.woheller69.gpscockpit;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;

import androidx.appcompat.app.AlertDialog;

public class GithubStar {
    public static void setAskForStar(boolean askForStar){
        SharedPreferences prefManager = Utils.getDefPrefs();
        SharedPreferences.Editor editor = prefManager.edit();
        editor.putBoolean("askForStar", askForStar);
        editor.apply();
    }

    static boolean shouldShowStarDialog() {
        SharedPreferences prefManager = Utils.getDefPrefs();
        int versionCode = prefManager.getInt("versionCode",0);
        boolean askForStar=prefManager.getBoolean("askForStar",true);

        if (prefManager.contains("versionCode") && BuildConfig.VERSION_CODE>versionCode && askForStar){ //not at first start, only after upgrade and only if use has not yet given a star or has declined
            SharedPreferences.Editor editor = prefManager.edit();
            editor.putInt("versionCode", BuildConfig.VERSION_CODE);
            editor.apply();
            return true;
        } else {
            SharedPreferences.Editor editor = prefManager.edit();
            editor.putInt("versionCode", BuildConfig.VERSION_CODE);
            editor.apply();
            return false;
        }
    }

    static void starDialog(Context context, String url){
        SharedPreferences prefManager = Utils.getDefPrefs();
        if (prefManager.getBoolean("askForStar",true))    {
            AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(context);
            alertDialogBuilder.setMessage(R.string.dialog_StarOnGitHub);
            alertDialogBuilder.setPositiveButton(context.getString(R.string.dialog_OK_button), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                    setAskForStar(false);
                }
            });
            alertDialogBuilder.setNegativeButton(context.getString(R.string.dialog_NO_button), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    setAskForStar(false);
                }
            });
            alertDialogBuilder.setNeutralButton(context.getString(R.string.dialog_Later_button), null);

            AlertDialog alertDialog = alertDialogBuilder.create();
            alertDialog.show();
        }
    }

}
