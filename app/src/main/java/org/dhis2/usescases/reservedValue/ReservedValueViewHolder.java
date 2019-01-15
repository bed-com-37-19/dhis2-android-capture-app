package org.dhis2.usescases.reservedValue;


import android.support.v7.widget.RecyclerView;
import org.dhis2.databinding.ItemReservedValueBinding;
import com.android.databinding.library.baseAdapters.BR;
public class ReservedValueViewHolder extends RecyclerView.ViewHolder {

    private ItemReservedValueBinding binding;
    private ReservedValueContracts.Presenter presenter;

    public ReservedValueViewHolder(ItemReservedValueBinding binding, ReservedValueContracts.Presenter presenter) {
        super(binding.getRoot());
        this.binding = binding;
        this.presenter = presenter;

    }

    public void bind(ReservedValueContracts.Presenter presenter, ReservedValueModel dataElement){
        //TODO cambiarlo en el xml tambien
        binding.setVariable(BR.dataElement, dataElement);
        binding.setVariable(BR.presenter, presenter);
    }

}
