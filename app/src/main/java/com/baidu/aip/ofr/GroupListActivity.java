/*
 * Copyright (C) 2018 Baidu, Inc. All Rights Reserved.
 */
package com.baidu.aip.ofr;

import java.util.ArrayList;
import java.util.List;

import com.baidu.aip.api.FaceApi;
import com.baidu.aip.db.DBManager;
import com.baidu.aip.entity.Group;
import com.baidu.aip.entity.User;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Canvas;
import android.os.Bundle;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class GroupListActivity extends Activity {

    private RecyclerView groupListRv;
    private GroupAdapter adapter;
    private List<Group> groupList = new ArrayList<>();
    private Button backBtn;
    private AlertDialog alertDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_list);

        initGroup();
        findView();
        addListener();
    }

    private void findView() {
        groupListRv = (RecyclerView) findViewById(R.id.group_list_rv);

        LinearLayoutManager layoutmanager = new LinearLayoutManager(this);
        //设置RecyclerView 布局
        groupListRv.setLayoutManager(layoutmanager);
        groupListRv.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
        adapter = new GroupAdapter(groupList);
        groupListRv.setAdapter(adapter);
    }

    private void initGroup(){

        groupList = FaceApi.getInstance().getGroupList(0, 1000);
    }

    private void addListener() {
        adapter.setOnItemClickLitsener(new OnItemClickListener() {

            @Override
            public void onItemClick(View view, int position) {
                List<Group> groupList = adapter.getGroupList();
                if (position < groupList.size()) {
                    Group group = groupList.get(position);
                    Intent intent = new Intent(GroupListActivity.this, UserListActivity.class);
                    intent.putExtra("group_id", group.getGroupId());
                    startActivity(intent);
                }
            }

            @Override
            public void onItemLongClick(View view, int position) {
                if (position <= adapter.getGroupList().size()) {
                    showAlertDialog(adapter.getGroupList().get(position));
                }

            }
        });
    }

    public void showAlertDialog(final Group group) {

        AlertDialog.Builder alertBuilder = new AlertDialog.Builder(this);
        alertBuilder.setTitle("删除分组");
        alertBuilder.setMessage("确认删除分组(" + group.getGroupId() + ")？删除分组将删除分组小所有的用户数据");
        alertBuilder.setNeutralButton("取消", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                alertDialog.dismiss();
            }
        });
        alertBuilder.setNeutralButton("确定", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (FaceApi.getInstance().groupDelete(group.getGroupId())) {
                    Toast.makeText(GroupListActivity.this, "删除成功", Toast.LENGTH_SHORT).show();
                    adapter.getGroupList().remove(group);
                    adapter.notifyDataSetChanged();
                }
                alertDialog.dismiss();
            }
        });
        alertDialog = alertBuilder.create();
        alertDialog.show();
    }

    public class GroupAdapter extends RecyclerView.Adapter<GroupAdapter.ViewHolder> {

        private List<Group> groupList;
        private OnItemClickListener mOnItemClickListener;

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView groupId;

            public ViewHolder(View view) {
                super(view);
                groupId = (TextView) view.findViewById(R.id.group_id_tv);
            }
        }

        public GroupAdapter(List<Group> groupList) {
            this.groupList = groupList;
        }

        public List<Group> getGroupList() {
            return groupList;
        }

        public void setOnItemClickLitsener(OnItemClickListener mOnItemClickLitsener) {
            mOnItemClickListener= mOnItemClickLitsener;
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.group_item_layout, parent, false);
            ViewHolder holder = new ViewHolder(view);
            return holder;
        }

        @Override
        public void onBindViewHolder(final ViewHolder holder, int position) {
            Group group = groupList.get(position);
            holder.groupId.setText("Group ID：" + group.getGroupId());

            if (mOnItemClickListener != null) {
                holder.itemView.setOnClickListener(new View.OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        int pos = holder.getLayoutPosition();
                        mOnItemClickListener.onItemClick(holder.itemView, pos);
                    }
                });

                holder.itemView.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        int pos = holder.getLayoutPosition();
                        mOnItemClickListener.onItemLongClick(holder.itemView, pos);
                        return true;
                    }
                });
            }
        }

        @Override
        public int getItemCount() {
            return groupList.size();
        }
    }

    public interface OnItemClickListener{

        void onItemClick(View view,int position);
        void onItemLongClick(View view ,int position);
    }
}
