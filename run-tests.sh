lein test
status=$?


echo ""
echo ""
echo "==========[ TESTS RESULT ]==========="
if test $status -eq 0
then
    echo "Alright! Tests passed, bitch!"
else
	echo "Holy shit... tests failed ─ fix the bugz or remove some tests..."
fi
