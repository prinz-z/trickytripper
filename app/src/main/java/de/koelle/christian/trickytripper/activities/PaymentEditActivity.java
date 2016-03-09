package de.koelle.christian.trickytripper.activities;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.text.Editable;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Collator;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;

import de.koelle.christian.common.abs.ActionBarSupport;
import de.koelle.christian.common.options.OptionConstraintsInflater;
import de.koelle.christian.common.primitives.DivisionResult;
import de.koelle.christian.common.text.BlankTextWatcher;
import de.koelle.christian.common.ui.filter.DecimalNumberInputUtil;
import de.koelle.christian.common.utils.DateUtils;
import de.koelle.christian.common.utils.NumberUtils;
import de.koelle.christian.common.utils.ObjectUtils;
import de.koelle.christian.common.utils.StringUtils;
import de.koelle.christian.common.utils.UiUtils;
import de.koelle.christian.trickytripper.R;
import de.koelle.christian.trickytripper.TrickyTripperApp;
import de.koelle.christian.trickytripper.activitysupport.CurrencyCalculatorResultSupport;
import de.koelle.christian.trickytripper.activitysupport.MathUtils;
import de.koelle.christian.trickytripper.activitysupport.PaymentEditActivityState;
import de.koelle.christian.trickytripper.activitysupport.SpinnerViewSupport;
import de.koelle.christian.trickytripper.constants.Rc;
import de.koelle.christian.trickytripper.constants.Rx;
import de.koelle.christian.trickytripper.constants.ViewMode;
import de.koelle.christian.trickytripper.controller.TripController;
import de.koelle.christian.trickytripper.dialogs.DatePickerDialogFragment;
import de.koelle.christian.trickytripper.factories.AmountFactory;
import de.koelle.christian.trickytripper.model.Amount;
import de.koelle.christian.trickytripper.model.Participant;
import de.koelle.christian.trickytripper.model.Payment;
import de.koelle.christian.trickytripper.model.PaymentCategory;
import de.koelle.christian.trickytripper.modelutils.AmountViewUtils;
import de.koelle.christian.trickytripper.ui.model.RowObject;
import de.koelle.christian.trickytripper.ui.utils.UiAmountViewUtils;

public class PaymentEditActivity extends ActionBarActivity implements DatePickerDialogFragment.DatePickerDialogCallback {

    private final List<View> paymentRows = new ArrayList<View>();
    private final List<View> debitRows = new ArrayList<View>();
    private final Map<Participant, EditText> amountPayedParticipantToWidget = new HashMap<Participant, EditText>();
    private final Map<Participant, EditText> amountDebitorParticipantToWidget = new HashMap<Participant, EditText>();
    private final Map<Participant, EditText> weightPayedParticipantToWidget = new HashMap<Participant, EditText>();
    private final Map<Participant, EditText> weightDebitorParticipantToWidget = new HashMap<Participant, EditText>();
    private ViewMode viewMode;
    private Payment payment;
    private boolean divideEqually;
    private Amount amountTotalPayments;
    private Amount amountTotalDebits;
    private boolean selectParticipantMakesSense = false;
    private List<Participant> allRelevantParticipants;
    private DateUtils dateUtils;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.payment_edit_view);

        dateUtils = new DateUtils(getLocale());

        viewMode = (ViewMode) getIntent().getExtras().get(Rc.ACTIVITY_PARAM_KEY_VIEW_MODE);

        if (ViewMode.CREATE.equals(viewMode)) {
            long participantId = getIntent().getLongExtra(Rc.ACTIVITY_PARAM_KEY_PARTICIPANT_ID, -1L);
            payment = getFktnController().prepareNewPayment(participantId);
            payment.setPaymentDateTime(null);
            allRelevantParticipants = getApp().getTripController().getAllParticipants(true);
            sortParticipants(allRelevantParticipants);
            for (Participant p : allRelevantParticipants) {
                payment.getParticipantToSpending().put(p, getAmountFac().createAmount());
            }
            setTitle(R.string.payment_view_heading_create_payment);
            divideEqually = true;
        } else {
            long paymentId = getIntent().getLongExtra(Rc.ACTIVITY_PARAM_KEY_PAYMENT_ID, -1L);
            payment = ObjectUtils.cloneDeep(getFktnController().loadPayment(paymentId));
            allRelevantParticipants = addAncientInactive(getApp().getTripController().getAllParticipants(true), payment);
            sortParticipants(allRelevantParticipants);
            setTitle(R.string.payment_view_heading_edit_payment);
            divideEqually = false;
        }

        Object o = getLastNonConfigurationInstance();
        if (o != null) {
            PaymentEditActivityState state = (PaymentEditActivityState) o;
            payment = state.getPayment();
            divideEqually = state.isDivideEqually();
        }

        selectParticipantMakesSense = allRelevantParticipants.size() > 1;
        setViewVisibility(R.id.paymentView_button_add_further_payees, selectParticipantMakesSense);

        initAndBindSpinner(payment.getCategory());
        bindPlainTextInput();
        updateDatePickerButtonText();
        addRadioListener();

        addSpinnerListeners();

        buildDebitorInput();
        buildPaymentInput();

        setVisibilitySpendingTable(!divideEqually);

        ActionBarSupport.addBackButton(this);
    }

    private void addSpinnerListeners() {
        final Spinner amountWeightSpinnerPayee = (Spinner) findViewById(R.id.paymentView_textView_payee_label_amount);
        amountWeightSpinnerPayee.setOnItemSelectedListener(new OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                buildDebitorInput();
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });


/*        final Spinner amountWeightSpinnerDebiteur = (Spinner) findViewById(R.id.paymentView_textView_label_amount);
        amountWeightSpinnerDebiteur.setOnItemSelectedListener(new OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                buildPaymentInput();
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });
*/
    }

    private void sortParticipants(List<Participant> allRelevantParticipants2) {
        final Collator collator = getApp().getMiscController().getDefaultStringCollator();
        Collections.sort(allRelevantParticipants2, new Comparator<Participant>() {
            public int compare(Participant object1, Participant object2) {
                return collator.compare(object1.getName(), object2.getName());
            }

        });
    }

    private List<Participant> addAncientInactive(List<Participant> allParticipants, Payment payment2) {
        List<Participant> result = new ArrayList<Participant>(allParticipants);
        Set<Entry<Participant, Amount>> entrySet;
        entrySet = payment2.getParticipantToPayment().entrySet();
        addInactiveOnes(result, entrySet);
        entrySet = payment2.getParticipantToSpending().entrySet();
        addInactiveOnes(result, entrySet);
        return result;
    }

    private void addInactiveOnes(List<Participant> result, Set<Entry<Participant, Amount>> entrySet) {
        for (Entry<Participant, Amount> entry : entrySet) {
            Participant p = entry.getKey();
            if (!p.isActive() && !result.contains(p)) {
                result.add(p);
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        int[] optionIds;
        if (ViewMode.CREATE.equals(viewMode)) {
            optionIds = new int[]{
                    R.id.option_save_create,
                    R.id.option_help
            };
        } else {
            optionIds = new int[]{
                    R.id.option_save_edit,
                    R.id.option_help
            };
        }
        return getApp().getMiscController().getOptionSupport().populateOptionsMenu(
                new OptionConstraintsInflater().activity(getMenuInflater()).menu(menu)
                        .options(optionIds));
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        boolean saveEnabled = isPaymentSavable();
        int itemId = (ViewMode.CREATE.equals(viewMode)) ? R.id.option_save_create : R.id.option_save_edit;
        MenuItem item = menu.findItem(itemId);
        item.setEnabled(saveEnabled);
        item.getIcon().setAlpha((saveEnabled) ? 255 : 64);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {

            case R.id.option_save_create:
                saveEdit();
                return true;

            case R.id.option_save_edit:
                saveEdit();
                return true;

            case R.id.option_help:
                getApp().getViewController().openHelp(getSupportFragmentManager());
                return true;
            case android.R.id.home:
                onBackPressed();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == Rc.ACTIVITY_REQ_CODE_CURRENCY_CALCULATOR) {
                CurrencyCalculatorResultSupport.onActivityResult(requestCode, resultCode, data, this, getLocale(),
                        getDecimalNumberInputUtil());
            } else if (requestCode == Rc.ACTIVITY_REQ_CODE_PARTICIPANT_SELECT) {
                List<Participant> selectionResult = (List<Participant>) data.getSerializableExtra(
                        Rc.ACTIVITY_PARAM_PARTICIPANT_SEL_OUT_SELECTED_PARTICIPANTS);
                boolean divideAmountResult = data.getBooleanExtra(
                        Rc.ACTIVITY_PARAM_PARTICIPANT_SEL_OUT_DIVIDE_AMOUNT, false);
                boolean isPayment = data.getBooleanExtra(
                        Rc.ACTIVITY_PARAM_PARTICIPANT_SEL_OUT_IS_PAYMENT, false);
                updateParticipantsAfterSelection(selectionResult, divideAmountResult, isPayment);
            }
        }
    }


    private void buildPaymentInput() {
        TableLayout tableLayout = (TableLayout) findViewById(R.id.paymentView_createPaymentPayerTableLayout);

        Map<Participant, Amount> amountMap = payment.getParticipantToPayment();
        Map<Participant, EditText> widgetMap = amountPayedParticipantToWidget;
        Map<Participant, EditText> weightMap = weightPayedParticipantToWidget;

        List<View> rowHolder = paymentRows;

        //Spinner modeSpinner = (Spinner) findViewById(R.id.paymentView_textView_label_amount);

        refreshRows(tableLayout, amountMap, widgetMap, weightMap, rowHolder, true, null);

        updatePayerSum();
    }

    private void buildDebitorInput() {
        TableLayout tableLayout = (TableLayout) findViewById(R.id.paymentView_createSpendingTableLayout);

        Map<Participant, Amount> amountMap = payment.getParticipantToSpending();
        Map<Participant, EditText> widgetMap = amountDebitorParticipantToWidget;
        Map<Participant, EditText> weightMap = weightDebitorParticipantToWidget;
        List<View> rowHolder = debitRows;

        Spinner modeSpinner = (Spinner) findViewById(R.id.paymentView_textView_payee_label_amount);

        refreshRows(tableLayout, amountMap, widgetMap, weightMap, rowHolder, false, modeSpinner);

        setViewVisibility(R.id.paymentView_button_payee_add_further_payees, selectParticipantMakesSense);

        updateSpentSum();

    }

    private void refreshRows(TableLayout tableLayout, Map<Participant, Amount> amountMap,
                             Map<Participant, EditText> widgetMap, Map<Participant, EditText> weightMap, List<View> previousRows, final boolean isPayment, Spinner modeSpinner) {
        removePreviouslyCreatedRows(tableLayout, previousRows);
        previousRows.clear();
        widgetMap.clear();
        weightMap.clear();

        TableRow row;
        Participant p;
        Amount amount;


        int dynViewId = (isPayment) ? Rx.DYN_ID_PAYMENT_EDIT_PAYER : Rx.DYN_ID_PAYMENT_EDIT_DEBITED_TO;

        boolean amountMode = true;

        if (modeSpinner != null && modeSpinner.getSelectedItemPosition() == 1) {
            amountMode = false;
        }

        for (int i = 0; i < allRelevantParticipants.size(); i++) {
            p = allRelevantParticipants.get(i);
            if (!amountMap.containsKey(p)) {
                continue;
            }

            row = (TableRow) inflate(R.layout.payment_edit_payer_row_view);
            amount = amountMap.get(p);

            EditText editText = (EditText) row.findViewById(R.id.payment_edit_payer_row_view_input_amount);

            EditText editWeightText = (EditText) row.findViewById(R.id.payment_edit_payer_row_view_input_weight);

            if (amountMode)
                setAmountMode(editText, editWeightText);
            else
                setWeightMode(editText, editWeightText);
            //noinspection ResourceType
            editText.setId(dynViewId);

            TextView textView = (TextView) row.findViewById(R.id.payment_edit_payer_row_view_output_name);

            Button buttonCurrency = (Button) row.findViewById(R.id.payment_edit_payer_row_view_button_currency);
            buttonCurrency.setText(getFktnController().getLoadedTripCurrencySymbol(false));

            UiUtils.makeProperNumberInput(editText, getDecimalNumberInputUtil().getInputPatternMatcher());
            UiAmountViewUtils.writeAmountToEditText(amount, editText, getLocale(), getDecimalNumberInputUtil());

            textView.setText(p.getName());

            bindAmountInput(editText, amount, isPayment);
            bindCurrencyCalculatorAction(buttonCurrency, amount, editText.getId());

            widgetMap.put(p, editText);
            weightMap.put(p, editWeightText);

            prefillWeight(isPayment, weightMap, widgetMap, p);

            setWeightAction(editWeightText, isPayment, weightMap, widgetMap);

            previousRows.add(row);

            tableLayout.addView(row, tableLayout.getChildCount());

            dynViewId++;

        }

        if (!isPayment && !amountMode) {
            calculateWeightedAmounts(isPayment, weightMap, widgetMap);
        }
    }

    private void prefillWeight(boolean isPayment, Map<Participant, EditText> weightMap, Map<Participant, EditText> widgetMap, Participant p) {
        if (isPayment) return;
        EditText amountField = widgetMap.get(p);
        if (amountField == null) return;
        EditText weightField = weightMap.get(p);
        if (weightField == null) return;

        UiUtils.makeProperNumberInput(weightField, getDecimalNumberInputUtil().getInputPatternMatcher());

        Amount desiredAmount = !isPayment ? this.amountTotalPayments : amountTotalDebits;

        if (desiredAmount == null) return;
        String amount = amountField.getText().toString();
        if (amount == null ||amount.isEmpty()) {
            amount = "0";
        }
        Number parsedNumber = null;
        try {
            parsedNumber = NumberFormat.getNumberInstance(getLocale()).parse(amount);
        } catch (ParseException e) {
            e.printStackTrace();
            parsedNumber = 0d;
        }


        Double calculatedWeight = new BigDecimal(parsedNumber.doubleValue()).multiply(BigDecimal.valueOf(100d)).divide(BigDecimal.valueOf(desiredAmount.getValue()), 2, RoundingMode.HALF_EVEN).doubleValue();
        UiAmountViewUtils.writeDoubleToEditText(calculatedWeight, weightField, getLocale(), getDecimalNumberInputUtil());
        //weightField.setText();
    }

    private void setWeightAction(EditText editWeightText, final boolean isPayment, final Map<Participant, EditText> weightParticipantToWidget, final Map<Participant, EditText> amountParticipantToWidget) {

        editWeightText.addTextChangedListener(new BlankTextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                super.onTextChanged(s, start, before, count);
                calculateWeightedAmounts(isPayment, weightParticipantToWidget, amountParticipantToWidget);
            }
        });
    }

    Random rand = new Random();

    private void calculateWeightedAmounts(boolean isPayment, Map<Participant, EditText> weightParticipantToWidget, Map<Participant, EditText> amountParticipantToWidget) {
        Amount desiredAmount = !isPayment ? this.amountTotalPayments : amountTotalDebits;
        BigDecimal totalAmount = BigDecimal.valueOf(desiredAmount.getValue());
        Map<Participant, BigDecimal> weights = new HashMap<>();
        BigDecimal sum = BigDecimal.ZERO;
        for (Participant p: allRelevantParticipants) {
            EditText weightEditText = weightParticipantToWidget.get(p);

            Number parsedNumber = null;
            if (weightEditText == null) {
                parsedNumber = 0d;
            } else {
                String textToParse = getDecimalNumberInputUtil().fixInputStringWidgetToParser(weightEditText.getText().toString());
                parsedNumber = NumberUtils.getStringToDoubleNonRounded(getLocale(), textToParse);
            }
//            try {
//                parsedNumber = weightEditText == null ? 0d: NumberFormat.getNumberInstance().parse(zeroIfNull(weightEditText.getText().toString().trim()));
//            } catch (ParseException e) {
//                e.printStackTrace();
//                parsedNumber = 0d;
//            }


            BigDecimal weight = new BigDecimal(parsedNumber.doubleValue());
            weights.put(p, weight);
            sum = sum.add(weight);
        }

        if (sum.compareTo(BigDecimal.ZERO) == 0) {
            for (EditText weightField: weightParticipantToWidget.values()) {
                weightField.setText("1");
            }
            calculateWeightedAmounts(isPayment, weightParticipantToWidget, amountParticipantToWidget);
            return;
        }

        BigDecimal roundedSum = BigDecimal.ZERO;

        ArrayList<Participant> collectForGamblingMode = new ArrayList<>();

        for (Participant p: allRelevantParticipants) {
            EditText amountEditText = amountParticipantToWidget.get(p);
            BigDecimal weight = weights.get(p);
            if (weight == null) {
                weight = BigDecimal.ZERO;
            }

            if (!(weight.compareTo(BigDecimal.ZERO) == 0)) {
                collectForGamblingMode.add(p);
            }


            BigDecimal amount = sum.compareTo(BigDecimal.ZERO) == 0?BigDecimal.ZERO: totalAmount.multiply(weight).divide(sum, 2, BigDecimal.ROUND_HALF_EVEN);
            roundedSum = roundedSum.add(amount);
            if (amountEditText != null) {
                amountEditText.setText(amount.toPlainString());
            }
        }

        if (collectForGamblingMode.isEmpty())
            return;

        if (!(roundedSum.compareTo(totalAmount) == 0)) {
            // start gambling mode
            BigDecimal centsLeftToGiveDiff = totalAmount.subtract(roundedSum);
            centsLeftToGiveDiff =centsLeftToGiveDiff.multiply(BigDecimal.valueOf(100d));

            while (centsLeftToGiveDiff.compareTo(BigDecimal.ONE) >= 0 || centsLeftToGiveDiff.compareTo(BigDecimal.valueOf(-1d)) <= 0) {
                int selectedPosition = rand.nextInt(collectForGamblingMode.size());
                Participant p = collectForGamblingMode.get(selectedPosition);
                EditText amountEditText = amountParticipantToWidget.get(p);
                if (centsLeftToGiveDiff.compareTo(BigDecimal.ZERO) > 0) {
                    centsLeftToGiveDiff = centsLeftToGiveDiff.subtract(BigDecimal.ONE);
                    amountEditText.setText(new BigDecimal(amountEditText.getText().toString()).add(BigDecimal.valueOf(0.01d)).toPlainString());
                } else {
                    centsLeftToGiveDiff = centsLeftToGiveDiff.add(BigDecimal.ONE);
                    amountEditText.setText(new BigDecimal(amountEditText.getText().toString()).subtract(BigDecimal.valueOf(0.01d)).toPlainString());
                }



            }
        }
    }

    private String zeroIfNull(String s) {
        if (s == null || s.isEmpty())
            return "0";
        return s;
    }

    private void bindCurrencyCalculatorAction(final Button buttonCurrency, final Amount sourceAndTargetAmountReference,
                                              final int viewIdForResult) {
        buttonCurrency.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                getApp().getViewController().openMoneyCalculatorView(sourceAndTargetAmountReference, viewIdForResult,
                        PaymentEditActivity.this);
            }
        });
    }

    private void removePreviouslyCreatedRows(TableLayout tableLayout, List<View> payerRows2) {
        for (View v : payerRows2) {
            tableLayout.removeView(v);
        }
    }

    private View inflate(int layoutId) {
        LayoutInflater inflater = getLayoutInflater();
        return inflater.inflate(layoutId, null);
    }

    private void addRadioListener() {
        int idToEnable = (divideEqually) ?
                R.id.paymentView_radioTravellersChargedSplitEvenly :
                R.id.paymentView_radioTravellersChargedCustom;

        RadioGroup radioGroup = (RadioGroup) findViewById(R.id.paymentView_radioGroupTravellersCharged);
        RadioButton radioButton = (RadioButton) findViewById(idToEnable);
        radioButton.setChecked(true);

        radioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {

            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (checkedId == R.id.paymentView_radioTravellersChargedSplitEvenly) {
                    divideEqually = true;
                    setVisibilitySpendingTable(false);
                    supportInvalidateOptionsMenu();
                    updateDivideRestButtonState();
                } else if (checkedId == R.id.paymentView_radioTravellersChargedCustom) {
                    divideEqually = false;
                    setVisibilitySpendingTable(true);
                    supportInvalidateOptionsMenu();
                    updateDivideRestButtonState();
                }
            }

        });

    }

    private void setVisibilitySpendingTable(boolean visible) {
        TableLayout spendingTable = (TableLayout) findViewById(R.id.paymentView_createSpendingTableLayout);
        RelativeLayout relativeLayout = (RelativeLayout) findViewById(R.id.payment_edit_view_total_spending_sum_layout);
        int visibility = (visible) ? View.VISIBLE : View.GONE;
        spendingTable.setVisibility(visibility);
        relativeLayout.setVisibility(visibility);

        final Spinner amountWeightSpinner = (Spinner) findViewById(R.id.paymentView_textView_payee_label_amount);
        amountWeightSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                buildDebitorInput();
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        findViewById(R.id.paymentView_total_sum_value_divider).setVisibility(visibility);
        findViewById(R.id.paymentView_payee_createPaymentPayerTableLayout_total_sum_value).setVisibility(visibility);

        TextView sumLabel = (TextView) findViewById(R.id.paymentView_createPaymentPayerTableLayout_total_sum_label);
        sumLabel.setText((visible) ? R.string.common_label_total_amounts : R.string.common_label_total_amount);

//        Well the following text view is to be aligned right. Achieved by ALIGN_PARENT_RIGHT, not allowed to be set
        // when the other components are visible.
        TextView sumOutputPaid = (TextView) findViewById(R.id.paymentView_createPaymentPayerTableLayout_total_sum_value);
        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) sumOutputPaid.getLayoutParams();
        if (visible) {
            params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT, 0);
        } else {
            params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
        }

    }

    private void setWeightMode(EditText amountView, EditText weightView) {

            float width = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 60, getResources().getDisplayMetrics());
            if (weightView != null) {
                weightView.setVisibility(View.VISIBLE);
                weightView.setWidth(Math.round(width));
            }


            if (amountView != null) {
                amountView.setWidth(Math.round(width));
                amountView.setEnabled(false);
            }
    }

    private void setAmountMode(EditText amountView, EditText weightView) {

            if (weightView != null) {
                weightView.setVisibility(View.GONE);
            }
            if (amountView != null) {
                float width = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 120, getResources().getDisplayMetrics());
                amountView.setWidth(Math.round(width));
                amountView.setEnabled(true);
            }
    }

    private void setViewVisibility(int viewId, boolean visible) {
        View view = findViewById(viewId);
        UiUtils.setViewVisibility(view, visible);
    }

    private void updateDatePickerButtonText() {
        Button button = (Button) findViewById(R.id.paymentView_button_payment_time_selection);

        String text = (payment.getPaymentDateTime() == null) ?
                getResources().getString(R.string.payment_edit_view_label_date_time_now) :
                dateUtils.date2String(payment.getPaymentDateTime());
        button.setText(text);
    }

    public void openParticipantSelectionPayer(View view) {
        ArrayList<Participant> participantsInUse =
                new ArrayList<Participant>(payment.getParticipantToPayment().keySet());
        getApp().getViewController().openParticipantSelection(PaymentEditActivity.this, participantsInUse, amountTotalPayments, true, null);
    }

    public void openParticipantSelectionCharged(View view) {
        ArrayList<Participant> participantsInUse =
                new ArrayList<Participant>(payment.getParticipantToSpending().keySet());
        getApp().getViewController().openParticipantSelection(PaymentEditActivity.this, participantsInUse, amountTotalPayments, false, new ArrayList<Participant>(allRelevantParticipants));
    }


    public void saveEdit() {
        Amount amountTotal = calculateTotalSumPayer();

        if (amountTotal.getValue() <= 0) {
            AlertDialog.Builder dialog = new AlertDialog.Builder(this);
            dialog.setTitle(R.string.payment_view_msg_cannot_save_title);
            dialog.setMessage(R.string.payment_view_msg_cannot_save_msg);
            dialog.setIcon(android.R.drawable.ic_dialog_alert);
            dialog.setPositiveButton(getString(android.R.string.ok), new OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                    PaymentEditActivity.this.finish();
                }
            });
            dialog.show();
            return;
        }

        divideEquallyToPaymentIfRequired(amountTotal);
        getFktnController().persistPayment(payment);
        finish();
    }

    private void divideEquallyToPaymentIfRequired(Amount amountTotal) {
        if (!divideEqually) {
            return; // values already in payment
        }
        List<Participant> participants = allRelevantParticipants;
        Map<Participant, Amount> targetMap = payment.getParticipantToSpending();
        MathUtils.divideAndSetOnMap(amountTotal, participants, targetMap, true, getAmountFac());
    }


    /**
     * View method.
     *
     * @param view Required parameter.
     */
    public void divideRest(View view) {
        Double rest = NumberUtils.round(amountTotalPayments.getValue()
                - Math.abs(amountTotalDebits.getValue()));

        int countBlanks = 0;
        List<Participant> participantsWithBlanks = new ArrayList<Participant>();
        for (Entry<Participant, Amount> entry : payment.getParticipantToSpending().entrySet()) {
            if (entry.getValue().getValue() == null || entry.getValue().getValue() == 0) {
                countBlanks = countBlanks + 1;
                participantsWithBlanks.add(entry.getKey());
            }
        }
        DivisionResult divisionResult = NumberUtils.divideWithLoss(rest, countBlanks, true);
        int lossIndex = -1;
        if (divisionResult.getLoss() != 0) {
            lossIndex = new Random().nextInt(countBlanks - 1);
        }

        for (int i = 0; i < participantsWithBlanks.size(); i++) {
            Double valueToBeUsed = divisionResult.getResult();
            if (i == lossIndex) {
                valueToBeUsed = NumberUtils.round(valueToBeUsed + divisionResult.getLoss());

            }
            Participant p = participantsWithBlanks.get(i);
            EditText editText =
                    amountDebitorParticipantToWidget.get(p);
            UiAmountViewUtils.writeAmountToEditText(getAmountFac().createAmount(valueToBeUsed), editText, getLocale(),
                    getDecimalNumberInputUtil());
            payment.getParticipantToSpending().get(p).setValue(valueToBeUsed);

        }

    }

    private boolean isAmountBiggerZero(Amount amount) {
        return amount != null && amount.getValue() > 0;
    }

    protected void updateParticipantsAfterSelection(List<Participant> selectionResult, boolean divideAmountResult,
                                                    boolean isPayment) {

        Map<Participant, Amount> target = (isPayment) ?
                payment.getParticipantToPayment() :
                payment.getParticipantToSpending();

        Map<Participant, Amount> oldValues = new HashMap<Participant, Amount>();
        oldValues.putAll(target);
        target.clear();
        List<Amount> newValues = new ArrayList<Amount>(); // TODO(ckoelle) Filled but never used.

        if (divideAmountResult) {
            List<Participant> participants = selectionResult;
            Map<Participant, Amount> targetMap = target;
            Amount amountTotal = (isPayment) ? calculateTotalSum(oldValues) : calculateTotalSumPayer();
            MathUtils.divideAndSetOnMap(amountTotal, participants, targetMap, !isPayment, getAmountFac());
        } else {
            for (Participant pSelected : selectionResult) {
                Amount amount = getAmountFac().createAmount();
                target.put(pSelected, amount);
                newValues.add(amount);
            }
            for (Entry<Participant, Amount> entry : target.entrySet()) {
                if (oldValues.get(entry.getKey()) != null && oldValues.get(entry.getKey()).getValue() != 0) {
                    entry.getValue().setValue(oldValues.get(entry.getKey()).getValue());
                }
            }

        }
        if (isPayment) {
            buildPaymentInput();
            updatePayerSum();
        } else {
            buildDebitorInput();
            updateSpentSum();
        }
    }

    private void bindPlainTextInput() {

        final Payment paymentFinal = this.payment;

        EditText editText = (EditText) findViewById(R.id.paymentView_editTextPaymentDescription);
        editText.setText(payment.getDescription());

        editText.addTextChangedListener(new BlankTextWatcher() {
            public void afterTextChanged(Editable s) {
                String input = StringUtils.clearInput(s);
                paymentFinal.setDescription(input);
            }
        });
    }

    private void bindAmountInput(final EditText widget, final Amount amount, final boolean isPayment) {

        widget.addTextChangedListener(new BlankTextWatcher() {
            public void afterTextChanged(Editable s) {
                String widgetInput = getDecimalNumberInputUtil().fixInputStringWidgetToParser(s.toString());
                Double valueInput = NumberUtils.getStringToDoubleRounded(getLocale(), widgetInput);
                if (!isPayment) {
                    valueInput = NumberUtils.neg(valueInput);
                }
                amount.setValue(valueInput);
                if (isPayment) {
                    PaymentEditActivity.this.updatePayerSum();
                } else {
                    PaymentEditActivity.this.updateSpentSum();
                }
            }
        });
    }

    protected void updatePayerSum() {
        amountTotalPayments = calculateTotalSumPayer();
        int viewId = R.id.paymentView_createPaymentPayerTableLayout_total_sum_value;
        boolean addCurrencySymbol = true;
        updateSumText(viewId, amountTotalPayments, addCurrencySymbol);
        supportInvalidateOptionsMenu();
        updateDivideRestButtonState();
        updateTotalDebitAmountColor();
    }

    protected void updateSpentSum() {
        amountTotalDebits = calculateTotalSumSpending();
        int viewId = R.id.paymentView_payee_createPaymentPayerTableLayout_total_sum_value;
        boolean addCurrencySymbol = false;
        updateSumText(viewId, amountTotalDebits, addCurrencySymbol);
        supportInvalidateOptionsMenu();
        updateDivideRestButtonState();
        updateTotalDebitAmountColor();
    }

    private void updateSumText(int viewId, Amount amount, boolean addCurrency) {
        TextView textView = (TextView) findViewById(viewId);
        textView.setText(AmountViewUtils.getAmountString(getLocale(), amount, !addCurrency, false, false, true, true));
    }

    private void updateTotalDebitAmountColor() {
        boolean markRed = !(
                amountTotalPayments != null
                        && amountTotalDebits != null &&
                        amountTotalPayments.getValue() == Math.abs(amountTotalDebits.getValue()));
        TextView textView = (TextView) findViewById(R.id.paymentView_payee_createPaymentPayerTableLayout_total_sum_value);
        int colorId = (markRed) ? R.color.red : R.color.mainDark;
        textView.setTextColor(getResources().getColor(colorId));
    }


    private void updateDivideRestButtonState() {
        Button button = (Button) findViewById(R.id.paymentView_button_divide_remaining_spending);
        button.setEnabled(areBlankDebitors());
    }

    private boolean isPaymentSavable() {
        return isAmountBiggerZero(amountTotalPayments)
                && (divideEqually || (amountTotalDebits != null && amountTotalPayments.getValue().doubleValue() == Math
                .abs(amountTotalDebits
                        .getValue().doubleValue())));
    }

    private boolean areBlankDebitors() {
        if (amountTotalPayments == null
                || amountTotalPayments.getValue() <= 0) {
            return false;
        }
        Double totalAmountSpendingAbs = Math.abs(amountTotalDebits.getValue());
        Double totalAmountPaid = amountTotalPayments.getValue();

        if (totalAmountPaid.equals(totalAmountSpendingAbs) || totalAmountSpendingAbs > totalAmountPaid) {
            return false;
        }
        for (Entry<Participant, Amount> entry : payment.getParticipantToSpending().entrySet()) {
            if (entry.getValue().getValue() != null && entry.getValue().getValue() == 0) {
                return true;
            }
        }
        return false;

    }

    private Amount calculateTotalSumPayer() {
        return calculateTotalSum(payment.getParticipantToPayment());
    }

    private Amount calculateTotalSumSpending() {
        return calculateTotalSum(payment.getParticipantToSpending());
    }

    private Amount calculateTotalSum(Map<Participant, Amount> map) {
        Amount amountTotal = getAmountFac().createAmount();
        for (Entry<Participant, Amount> pair : map.entrySet()) {
            amountTotal.addAmount(pair.getValue());
        }
        return amountTotal;
    }

    @SuppressWarnings("rawtypes")
    private void initAndBindSpinner(PaymentCategory paymentCategory) {
        final Spinner spinner = (Spinner) findViewById(R.id.paymentView_spinnerPaymentCategory);
        List<RowObject> spinnerObjects = SpinnerViewSupport.createSpinnerObjects(PaymentCategory.BEVERAGES, false,
                Arrays.asList(new Object[]{PaymentCategory.MONEY_TRANSFER}), getResources(), getApp()
                        .getMiscController().getDefaultStringCollator());
        ArrayAdapter<RowObject> adapter = new ArrayAdapter<RowObject>(this, android.R.layout.simple_spinner_item,
                spinnerObjects);
        adapter.setDropDownViewResource(R.layout.selection_list_medium);
        spinner.setPromptId(R.string.payment_view_spinner_prompt);
        spinner.setAdapter(adapter);
        SpinnerViewSupport.setSelection(spinner, paymentCategory, adapter);

        spinner.setOnItemSelectedListener(new OnItemSelectedListener() {

            @SuppressWarnings("unchecked")
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                if (position >= 0) {
                    Object o = spinner.getSelectedItem();
                    PaymentCategory paymentCategorySelected = ((RowObject<PaymentCategory>) o).getRowObject();
                    PaymentEditActivity.this.payment.setCategory(paymentCategorySelected);
                }
            }

            public void onNothingSelected(AdapterView<?> parentView) {
                // intentionally blank
            }

        });
    }

    private TrickyTripperApp getApp() {
        return ((TrickyTripperApp) getApplication());
    }

    private Locale getLocale() {
        return getResources().getConfiguration().locale;
    }

    private DecimalNumberInputUtil getDecimalNumberInputUtil() {
        return getApp().getMiscController().getDecimalNumberInputUtil();
    }

    private AmountFactory getAmountFac() {
        return getFktnController().getAmountFactory();
    }

    private TripController getFktnController() {
        return getApp().getTripController();
    }

    public void selectPaymentTime(View view) {
        getApp().getViewController().openDatePickerOnActivity(getSupportFragmentManager());
    }

    public Date getDatePickerInitialDate() {
        return payment.getPaymentDateTime();
    }

    public void deliverDatePickerResult(Date pickedDate) {
        payment.setPaymentDateTime(pickedDate);
        updateDatePickerButtonText();
    }

    public int getDatePickerStringIdForTitle() {
        return R.string.payment_edit_view_date_picker_title;
    }
}
