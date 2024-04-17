package com.tbuonomo.viewpagerdotsindicator;

import android.content.*;
import android.util.TypedValue;
import android.content.res.Resources;

public class UiUtils {
  public static int getThemePrimaryColor(final Context context) {
    final TypedValue value = new TypedValue();
    context.getTheme().resolveAttribute(androidx.appcompat.R.attr.colorPrimary, value, true);
    return value.data;
  }
}
