package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;

import java.util.Arrays;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {

    private EditText amountEditText;
    private Button convertButton;
    private TextView resultTextView;
    private Spinner fromCurrencySpinner, toCurrencySpinner;
    private RadioGroup operationRadioGroup;
    private TextView tvGreeting;
    private Button btnLogout;
    private FirebaseAuth mAuth;
    private List<Currency> currencyRates;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable runnable;

    private final List<String> currencyCodes = Arrays.asList("USD", "EUR", "GBP", "PLN", "UAH");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Ініціалізація Firebase Auth
        mAuth = FirebaseAuth.getInstance();

        // Ініціалізація елементів інтерфейсу
        amountEditText = findViewById(R.id.amountEditText);
        convertButton = findViewById(R.id.convertButton);
        resultTextView = findViewById(R.id.resultTextView);
        fromCurrencySpinner = findViewById(R.id.fromCurrencySpinner);
        toCurrencySpinner = findViewById(R.id.toCurrencySpinner);
        operationRadioGroup = findViewById(R.id.operationRadioGroup);
        tvGreeting = findViewById(R.id.tvGreeting);
        btnLogout = findViewById(R.id.btnLogout);

        // Відображення привітання
        String fullName = getIntent().getStringExtra("userFullName");
        if (fullName != null && !fullName.isEmpty()) {
            tvGreeting.setText("Вітання, " + fullName);
        } else {
            tvGreeting.setText("Вітання, Користувач");
        }

        // Налаштування адаптера для спінерів із кодами валют
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, currencyCodes);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        fromCurrencySpinner.setAdapter(adapter);
        toCurrencySpinner.setAdapter(adapter);

        // Отримання початкових курсів валют
        fetchCurrencyData();

        // Налаштування періодичного оновлення кожні 30 секунд
        runnable = new Runnable() {
            @Override
            public void run() {
                fetchCurrencyData();
                handler.postDelayed(this, 30_000);
            }
        };
        handler.postDelayed(runnable, 30_000);

        // Обробка натискання кнопки конвертації
        convertButton.setOnClickListener(v -> {
            if (currencyRates == null || currencyRates.isEmpty()) {
                Toast.makeText(MainActivity.this, "Курси валют не завантажено", Toast.LENGTH_SHORT).show();
                return;
            }

            String fromCurrency = fromCurrencySpinner.getSelectedItem().toString();
            String toCurrency = toCurrencySpinner.getSelectedItem().toString();

            if (fromCurrency.equals(toCurrency)) {
                Toast.makeText(MainActivity.this, "Валюти мають бути різними", Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                double amount = Double.parseDouble(amountEditText.getText().toString());
                String operation = getSelectedOperation();
                double rate = getRate(fromCurrency, toCurrency, operation);
                if (rate == -1) {
                    Toast.makeText(MainActivity.this, "Конвертація не вдалася: курси недоступні", Toast.LENGTH_SHORT).show();
                    return;
                }
                double convertedAmount = amount * rate;
                resultTextView.setText(String.format("%.2f %s", convertedAmount, toCurrency));
            } catch (NumberFormatException e) {
                Toast.makeText(MainActivity.this, "Некоректна сума", Toast.LENGTH_SHORT).show();
            }
        });

        // Обробка натискання кнопки виходу
        btnLogout.setOnClickListener(v -> {
            mAuth.signOut();
            Intent intent = new Intent(this, LoginActivity.class);
            startActivity(intent);
            finish();
        });
    }

    private void fetchCurrencyData() {
        ApiService apiService = NetworkService.getInstance().getMonoAPI();
        apiService.getCurrencyRates().enqueue(new Callback<List<Currency>>() {
            @Override
            public void onResponse(Call<List<Currency>> call, Response<List<Currency>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    currencyRates = response.body();
                    for (Currency currency : currencyRates) {
                        Log.d("CurrencyRates", "CodeA: " + currency.getCurrencyCodeA() +
                                ", CodeB: " + currency.getCurrencyCodeB() +
                                ", RateBuy: " + currency.getRateBuy() +
                                ", RateSell: " + currency.getRateSell() +
                                ", RateCross: " + currency.getRateCross());
                    }
                    Toast.makeText(MainActivity.this, "Курси оновлено", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(MainActivity.this, "Не вдалося завантажити курси", Toast.LENGTH_SHORT).show();
                    Log.e("CurrencyRates", "Response failed: " + response.code() + ", " + response.message());
                }
            }

            @Override
            public void onFailure(Call<List<Currency>> call, Throwable t) {
                Toast.makeText(MainActivity.this, "Помилка мережі: " + t.getMessage(), Toast.LENGTH_LONG).show();
                Log.e("CurrencyRates", "Network error: ", t);
            }
        });
    }

    private String getSelectedOperation() {
        int selectedId = operationRadioGroup.getCheckedRadioButtonId();
        if (selectedId == R.id.radioBuy) {
            return "Buy";
        } else if (selectedId == R.id.radioSell) {
            return "Sell";
        } else if (selectedId == R.id.radioNBU) {
            return "NBU";
        }
        return "Buy"; // Значення за замовчуванням
    }

    private double getRate(String fromCurrency, String toCurrency, String operation) {
        int fromCode = getCurrencyCode(fromCurrency);
        int toCode = getCurrencyCode(toCurrency);

        if (fromCode == toCode) {
            return 1.0;
        }

        // Пошук прямого курсу (fromCurrency → toCurrency або toCurrency → fromCurrency)
        for (Currency currency : currencyRates) {
            if (currency.getCurrencyCodeA() == fromCode && currency.getCurrencyCodeB() == toCode) {
                double rate = getRateByOperation(currency, operation);
                Log.d("getRate", "Прямий курс: " + fromCurrency + " -> " + toCurrency + " = " + rate);
                return rate;
            } else if (currency.getCurrencyCodeA() == toCode && currency.getCurrencyCodeB() == fromCode) {
                double rate = getRateByOperation(currency, operation);
                if (rate != 0) {
                    Log.d("getRate", "Зворотний курс: " + toCurrency + " -> " + fromCurrency + " = " + rate);
                    return 1 / rate;
                }
            }
        }

        // Спроба подвійної конвертації через UAH
        double rateFromUAH = getRate(fromCurrency, "UAH", operation);
        double rateToUAH = getRate("UAH", toCurrency, operation);

        if (rateFromUAH != -1 && rateToUAH != -1) {
            double rate = rateFromUAH * rateToUAH;
            Log.d("getRate", "Подвійна конвертація через UAH: " + fromCurrency + " -> UAH (" + rateFromUAH + ") -> " + toCurrency + " (" + rateToUAH + ") = " + rate);
            return rate;
        }

        Log.e("getRate", "Курс не знайдено для " + fromCurrency + " -> " + toCurrency);
        return -1; // Курс не знайдено
    }

    private double getRateByOperation(Currency currency, String operation) {
        switch (operation) {
            case "Buy":
                return currency.getRateBuy() != 0 ? currency.getRateBuy() : currency.getRateCross();
            case "Sell":
                return currency.getRateSell() != 0 ? currency.getRateSell() : currency.getRateCross();
            case "NBU":
                return currency.getRateCross() != 0 ? currency.getRateCross() : currency.getRateBuy();
            default:
                return -1;
        }
    }

    private int getCurrencyCode(String currency) {
        switch (currency) {
            case "USD": return 840;
            case "EUR": return 978;
            case "GBP": return 826;
            case "PLN": return 985;
            case "UAH": return 980;
            default: return -1;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(runnable); // Зупинка періодичних оновлень
    }
}